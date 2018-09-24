package edu.mcw.rgd.search.indexer;

import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.StringMapQuery;
import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.ontologyx.Ontology;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import edu.mcw.rgd.datamodel.search.CommonWords;
import edu.mcw.rgd.process.Utils;

import java.sql.ResultSet;
import java.util.*;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author jdepons
 * @since May 27, 2008
 * Indexes rgd data into rgd_index table.  Each method runs independent of the others
 */
public class Spider {

    AliasDAO aliasDAO = new AliasDAO();
    AnnotationDAO annotationDAO = new AnnotationDAO();
    GeneDAO geneDAO = new GeneDAO();
    NotesDAO notesDAO = new NotesDAO();
    OntologyXDAO ontologyXDAO = new OntologyXDAO();
    QTLDAO qtlDAO = new QTLDAO();
    ReferenceDAO refDAO = new ReferenceDAO();
    SearchDAO searchDAO = new SearchDAO();
    SSLPDAO sslpDAO = new SSLPDAO();
    StrainDAO strainDAO = new StrainDAO();
    XdbIdDAO xdbIdDAO = new XdbIdDAO();

    Pattern pattern = Pattern.compile("[a-zA-Z_0-9\\-\\/]+");
    Pattern pattern2 = Pattern.compile("[a-zA-Z_0-9]+");
    Pattern pattern3 = Pattern.compile("[\\S]+");

    private boolean canDeleteFromIndex = true;
    private int deleteBatchSize;

    public boolean isCanDeleteFromIndex() {
        return canDeleteFromIndex;
    }

    public void setCanDeleteFromIndex(boolean canDeleteFromIndex) {
        this.canDeleteFromIndex = canDeleteFromIndex;
    }

    public void disableIndexes() throws Exception {
        searchDAO.disableIndexesForTable("RGD_INDEX");
    }

    public void enableIndexes() throws Exception {
        searchDAO.enableIndexesForTable("RGD_INDEX");
    }

    /**
     * Adds a row into the rgd_index table
     * @param rgdId rgd id of object being indexed
     * @param value value of objected being indexed
     * @param objectType object type
     * @param fieldType field type
     * @param speciesTypeKey species type key
     * @throws Exception if something wrong happens in spring framework
     */
    public void index(int rgdId, String value, String objectType, String fieldType, int speciesTypeKey) throws Exception {
        this.index(Integer.toString(rgdId), value, objectType, fieldType,speciesTypeKey);
    }

    /**
     * Removes all data from the rgd_index table
     * @throws Exception if something wrong happens in spring framework
     */
    public void truncateIndex() throws Exception {
        searchDAO.truncateIndex();
    }

    /**
     * Adds a row into the rgd_index table
     * @param rgdId rgd id of object being indexed
     * @param value value of objected being indexed; it is always converted to lower case
     * @param objectType object type
     * @param fieldType field type
     * @param speciesTypeKey species type key
     * @throws Exception if something wrong happens in spring framework
     */
    public void index(String rgdId, String value, String objectType, String fieldType, int speciesTypeKey) throws Exception {
        if (value == null)
            return;
        value = value.toLowerCase();

        if (searchDAO.isInIndex(rgdId, value, objectType, fieldType, speciesTypeKey)) return;

        // compute rank for a term
        int rank = computeRank(fieldType, value);

        //remove superscript stuff for index
        if (fieldType.equals("symbol")) {
            value = value.replaceAll("<i>|</i>|<sup>|</sup>","/");
        }

        // break value into words
        // map all of the words to the number of occurrences
        Map<String, Integer> words = buildWordHitMap(value);

        // put the words into database
        for( Map.Entry<String, Integer> entry: words.entrySet() ) {

            // for word with multiple occurrences, modify the rank appropriately
            int occurrenceCount = entry.getValue();
            int modifiedRank = rank * occurrenceCount;

            String word = entry.getKey();
            if( word.equals(value) ) { // exact match of word with incoming value
                modifiedRank += 100;
            }
            lazyInsertIntoRgdIndex(rgdId, word, objectType, fieldType, speciesTypeKey, modifiedRank);
        }
    }

    final int BATCH_SIZE = 10000;
    List<Object[]> _toBeInsertedData = new ArrayList<>();

    void lazyInsertIntoRgdIndex(String rgdId, String value, String type, String column, int species, int rank) throws Exception {
        _toBeInsertedData.add(new Object[]{rgdId, value, type, column, species, rank});
        if( _toBeInsertedData.size()>=BATCH_SIZE ) {
            doInsertsIntoRgdIndex();
        }
    }

    void doInsertsIntoRgdIndex() throws Exception {
        if( _toBeInsertedData.size()>0 ) {
            searchDAO.insertIntoRgdIndex(_toBeInsertedData);
            _toBeInsertedData.clear();
        }
    }

    // compute rank based on field type and length of value
    int computeRank(String fieldType, String value) {

        // Rank algorithm.  All values start with 100 points.  longer terms receive less of a score
        int rank = 100;

        switch (fieldType) {
            case "id":
                rank = 300;
                break;
            case "symbol":
                rank = 300;
                break;
            case "old_gene_symbol":
                rank = 200;
                break;
            case "name":
                rank = 100;
                break;
            case "old_gene_name":
                rank = 50;
                break;
            case "title":
                rank = 100;
                break;
            case "transcript_rgd_id":
                rank = 220;
                break;
            case "first_author":
                rank = 150;
                break;
        }

        if (value.length() < 100) {
            rank = rank + (100 - value.length());
        }

        return rank;
    }

    Map<String, Integer> buildWordHitMap(String value) {

        Map<String, Integer> words = new HashMap<>();

        updateWordHitMap(pattern, words, value, true);
        updateWordHitMap(pattern2, words, value, false);
        updateWordHitMap(pattern3, words, value, false);

        // add the value itself to the words map if it is not there
        if( words.get(value)==null )
            words.put(value, 1);

        return words;
    }


    void updateWordHitMap(Pattern pat, Map<String, Integer> words, String value, boolean firstMatcher) {
        // break value into words
        Matcher matcher = pat.matcher(value);
        while (matcher.find()) {
            // Get all groups for this match
            for (int i=0; i<=matcher.groupCount(); i++) {
                String word = matcher.group(i);
                // skip common words
                if (CommonWords.isCommon(word)) {
                    continue;
                }

                // increment occurrence count for this word
                Integer occurrenceCount = words.get(word);
                if( firstMatcher ) {
                    if( occurrenceCount==null )
                        occurrenceCount = 0; // first occurrence of this word
                    words.put(word, 1+occurrenceCount);
                } else {
                    if( occurrenceCount==null )
                        words.put(word, 1);
                }
            }
        }
    }

    public void finish() throws Exception {
        doInsertsIntoRgdIndex();
    }

    public void indexGeneSymbols() throws Exception{
        deleteFromIndex("GENES", "symbol");
        deleteFromIndex("GENES", "id");

        for (Gene gene: geneDAO.getAllActiveGenes()) {
            this.index(gene.getRgdId(),gene.getSymbol(), "GENES", "symbol", gene.getSpeciesTypeKey());
            this.index(gene.getRgdId(),gene.getRgdId() + "", "GENES", "id", gene.getSpeciesTypeKey());
        }
    }

    public void indexGeneNames() throws Exception{

        deleteFromIndex("GENES", "name");

        for (Gene gene: geneDAO.getAllActiveGenes()) {
            this.index(gene.getRgdId(),gene.getName(), "GENES", "name", gene.getSpeciesTypeKey());
        }
    }

    public void indexGeneDescriptions() throws Exception{

        deleteFromIndex("GENES", "description");

        for (Gene gene: geneDAO.getAllActiveGenes()) {
            String desc = Utils.getGeneDescription(gene); // this common method is used to display gene description on report pages
            this.index(gene.getRgdId(), desc, "GENES", "description", gene.getSpeciesTypeKey());
        }
    }

    public void indexGeneTranscripts() throws Exception{

        deleteFromIndex("GENES", "transcript_rgd_id");

        TranscriptDAO tdao = new TranscriptDAO();
        for (Gene gene: geneDAO.getAllActiveGenes()) {
            for( Transcript tr: tdao.getTranscriptsForGene(gene.getRgdId()) ) {
                this.index(gene.getRgdId(), Integer.toString(tr.getRgdId()), "GENES", "transcript_rgd_id", gene.getSpeciesTypeKey());
            }
        }
    }

    public void indexQTLSymbols() throws Exception{

        deleteFromIndex("QTLS", "symbol");

        for( QTL qtl: qtlDAO.getActiveQTLs() ) {
            this.index(qtl.getRgdId(),qtl.getSymbol(), "QTLS", "symbol", qtl.getSpeciesTypeKey());
        }
    }

    public void indexQTLNames() throws Exception{

        deleteFromIndex("QTLS", "name");

        for( QTL qtl: qtlDAO.getActiveQTLs() ) {
            this.index(qtl.getRgdId(),qtl.getName(), "QTLS", "name", qtl.getSpeciesTypeKey());
        }
    }

    public void indexSSLPSymbols() throws Exception{

        deleteFromIndex("SSLPS", "symbol");

        for( SSLP obj: sslpDAO.getActiveSSLPs() ) {
            this.index(obj.getRgdId(),obj.getName(), "SSLPS", "symbol", obj.getSpeciesTypeKey());
        }
    }

    public void indexReferenceCitations() throws Exception{

        deleteFromIndex("REFERENCES", "citation");

        for( Reference obj: refDAO.getActiveReferences() ) {
            this.index(obj.getRgdId(),obj.getCitation(), "REFERENCES", "citation", obj.getSpeciesTypeKey());
        }
    }

    public void indexReferenceTitles() throws Exception{

        deleteFromIndex("REFERENCES", "title");

        for( Reference obj: refDAO.getActiveReferences() ) {
            this.index(obj.getRgdId(),obj.getTitle(), "REFERENCES", "title", obj.getSpeciesTypeKey());
        }
    }

    public void indexReferenceAuthors() throws Exception{

        deleteFromIndex("REFERENCES", "author");

        for( Reference obj: refDAO.getActiveReferences() ) {
            int authorNr = 0;
            for( Author author: refDAO.getAuthors(obj.getKey()) ) {
                String fieldType = "author";
                if( authorNr==0 )
                    fieldType = "first_author";
                this.index(obj.getRgdId(), author.getLastName(), "REFERENCES", fieldType, obj.getSpeciesTypeKey());
                authorNr++;
            }
        }
    }

    public void indexReferencePubYears() throws Exception{

        deleteFromIndex("REFERENCES", "pub_year");

        for( Reference obj: refDAO.getActiveReferences() ) {
            Date pubDate = obj.getPubDate();
            if( pubDate==null )
                continue;
            String pubYear = Integer.toString(pubDate.getYear()+1900);
            this.index(obj.getRgdId(), pubYear, "REFERENCES", "pub_year", obj.getSpeciesTypeKey());
        }
    }

    public void indexStrainSymbols() throws Exception{

        deleteFromIndex("STRAINS", "symbol");

        for( Strain obj: strainDAO.getActiveStrains() ) {
            this.index(obj.getRgdId(),obj.getSymbol(), "STRAINS", "symbol", obj.getSpeciesTypeKey());
        }
    }

    public void indexStrainNames() throws Exception{

        deleteFromIndex("STRAINS", "name");

        for( Strain obj: strainDAO.getActiveStrains() ) {
            this.index(obj.getRgdId(),obj.getName(), "STRAINS", "name", obj.getSpeciesTypeKey());
        }
    }

    public void indexStrainSources() throws Exception{

        deleteFromIndex("STRAINS", "source");

        for( Strain obj: strainDAO.getActiveStrains() ) {
            this.index(obj.getRgdId(),obj.getSource(), "STRAINS", "source", obj.getSpeciesTypeKey());
        }
    }

    public void indexStrainOrigins() throws Exception{

        deleteFromIndex("STRAINS", "origin");

        for( Strain obj: strainDAO.getActiveStrains() ) {
            this.index(obj.getRgdId(),obj.getOrigin(), "STRAINS", "origin", obj.getSpeciesTypeKey());
        }
    }

    public void indexStrainTypes() throws Exception{

        deleteFromIndex("STRAINS", "type");

        for( Strain obj: strainDAO.getActiveStrains() ) {
            this.index(obj.getRgdId(),obj.getStrainTypeName(), "STRAINS", "type", obj.getSpeciesTypeKey());
        }
    }

    public void indexQTLTraits() throws Exception{

        deleteFromIndex("QTLS", "trait");

        for( QTL qtl: qtlDAO.getActiveQTLs() ) {
            // show VT term asociated with this QTL or if not available 'qtl_trait' note
            String traitTerm = null;
            for( StringMapQuery.MapPair pair: annotationDAO.getAnnotationTermAccIds(qtl.getRgdId(), "V") ) {
                traitTerm = pair.stringValue+" ("+pair.keyValue+")";
            }
            if( traitTerm==null ) {
                List<Note> notes = notesDAO.getNotes(qtl.getRgdId(), "qtl_trait");
                if( !notes.isEmpty() ) {
                    traitTerm = notes.get(0).getNotes();
                }
            }

            this.index(qtl.getRgdId(), traitTerm, "QTLS", "trait", qtl.getSpeciesTypeKey());
        }
    }

    public void indexQTLSubTraits() throws Exception{

        deleteFromIndex("QTLS", "subtrait");

        for( QTL qtl: qtlDAO.getActiveQTLs() ) {
            // show CMO term asociated with this QTL or if not available 'qtl_subtrait' note
            String traitTerm = null;
            for( StringMapQuery.MapPair pair: annotationDAO.getAnnotationTermAccIds(qtl.getRgdId(), "L") ) {
                traitTerm = pair.stringValue+" ("+pair.keyValue+")";
            }
            if( traitTerm==null ) {
                List<Note> notes = notesDAO.getNotes(qtl.getRgdId(), "qtl_subtrait");
                if( !notes.isEmpty() ) {
                    traitTerm = notes.get(0).getNotes();
                }
            }

            this.index(qtl.getRgdId(), traitTerm, "QTLS", "subtrait", qtl.getSpeciesTypeKey());
        }
    }

    public void indexExternalIdentifiers() throws Exception {

        Set<String> idTypes = new HashSet<>();

        ResultSet rs = xdbIdDAO.getExternalIdsResultSet();
        ExternalIdentifierXRef xref;
        while( (xref=xdbIdDAO.getNextExternalIdentifierXRef(rs))!=null ) {

            // limit processed xdb ids only to genes, sslps, qtls, strains and references
            if( !xref.getObjectType().equals("GENES") &&
                !xref.getObjectType().equals("SSLPS") &&
                !xref.getObjectType().equals("QTLS") &&
                !xref.getObjectType().equals("STRAINS") &&
                !xref.getObjectType().equals("VARIANTS") &&
                !xref.getObjectType().equals("REFERENCES") ) {
                continue;
            }

            String idType = xref.getObjectType() + "|" + xref.getIdType();

            // delete previous id types
            if( !idTypes.contains(idType) ) {
                idTypes.add(idType);
                deleteFromIndex(xref.getObjectType(), xref.getIdType());
            }

            this.index(xref.getRgdId(),xref.getExId(),xref.getObjectType(), xref.getIdType(), xref.getSpeciesTypeKey());
        }
    }

    public void indexDebug() throws Exception {
        this.index("RDO:0013598", "Woolly Hair, Autosomal Recessive", "RDO", "term", 0);
    }

    public void indexOntologyTerms() throws Exception {

        for( Ontology ont: ontologyXDAO.getPublicOntologies() ) {

            // delete previous ont ids
            deleteFromIndex(ont.getId(), "term");

            for( Term t: ontologyXDAO.getActiveTerms(ont.getId()) ) {
                this.index(t.getAccId(), t.getTerm(), t.getOntologyId(), "term", 0);
                this.index(t.getAccId(), t.getAccId(), t.getOntologyId(), "term", 0);
            }
        }
    }

    public void indexOntologyDefinitions() throws Exception {

        for( Ontology ont: ontologyXDAO.getPublicOntologies() ) {

            // delete previous ont ids
            deleteFromIndex(ont.getId(), "def");

            for( Term t: ontologyXDAO.getActiveTerms(ont.getId()) ) {
                this.index(t.getAccId(), t.getDefinition(), t.getOntologyId(), "def", 0);
            }
        }
    }

    public void indexOntologySynonyms() throws Exception {

        for( Ontology ont: ontologyXDAO.getPublicOntologies() ) {

            // delete previous ont ids
            deleteFromIndex(ont.getId(), "synonym");

            for( TermSynonym ts: ontologyXDAO.getActiveSynonyms(ont.getId()) ) {
                this.index(ts.getTermAcc(), ts.getName(), ont.getId(), "synonym" , 0);
            }
        }
    }

    public void indexAliases() throws Exception {

        indexAliases(RgdId.OBJECT_KEY_GENES, "GENES");
        indexAliases(RgdId.OBJECT_KEY_SSLPS, "SSLPS");
        indexAliases(RgdId.OBJECT_KEY_STRAINS, "STRAINS");
        indexAliases(RgdId.OBJECT_KEY_QTLS, "QTLS");
        indexAliases(RgdId.OBJECT_KEY_CELL_LINES, "CELL_LINES");
        indexAliases(RgdId.OBJECT_KEY_REFERENCES, "REFERENCES");
    }

    private void indexAliases(int objectKey, String objName) throws Exception {

        Set<String> idTypes = new HashSet<>();

        for( Alias alias: aliasDAO.getActiveAliases(objectKey) ) {

            String aliasType = alias.getTypeName()==null?"alias":alias.getTypeName();
            String idType = objName + "|" + aliasType;

            // delete previous id types
            if( !idTypes.contains(idType) ) {
                idTypes.add(idType);
                deleteFromIndex(objName, aliasType);
            }

            this.index(alias.getRgdId(), alias.getValue(), objName, aliasType, alias.getSpeciesTypeKey());
        }
    }

    /**
     * index symbols of active genomic elements found in GENOMIC_ELEMENTS table
     * @return count of genomic elements indexed
     * @throws Exception
     */
    public int indexGenomicElementSymbols() throws Exception{

        GenomicElementDAO dao = new GenomicElementDAO();
        AssociationDAO assocDAO = new AssociationDAO();

        int objsIndexed = 0;

        deleteFromIndex("CELL_LINES", "symbol");
        for( GenomicElement obj: dao.getActiveElements(RgdId.OBJECT_KEY_CELL_LINES) ) {
            this.index(obj.getRgdId(), obj.getSymbol(), "CELL_LINES", "symbol", obj.getSpeciesTypeKey());
            objsIndexed++;
        }

        deleteFromIndex("PROMOTERS", "symbol");
        deleteFromIndex("GENES", "promoter");
        for( GenomicElement obj: dao.getActiveElements(RgdId.OBJECT_KEY_PROMOTERS) ) {
            this.index(obj.getRgdId(), obj.getSymbol(), "PROMOTERS", "symbol", obj.getSpeciesTypeKey());
            objsIndexed++;

            for( Association assoc: assocDAO.getAssociationsForMasterRgdId(obj.getRgdId(), "promoter_to_gene") ) {
                this.index(assoc.getDetailRgdId(), obj.getSymbol(), "GENES", "promoter", obj.getSpeciesTypeKey());
                objsIndexed++;
            }
        }

        return objsIndexed;
    }

    /**
     * index symbol, name, type, so_acc_id, clinical significance and trait name fields for VARIANTS
     * @return count of variants indexed
     * @throws Exception
     */
    public int indexVariants() throws Exception{

        VariantInfoDAO dao = new VariantInfoDAO();

        int objsIndexed = 0;

        deleteFromIndex("VARIANTS", "symbol");
        deleteFromIndex("VARIANTS", "type");
        deleteFromIndex("VARIANTS", "name");
        deleteFromIndex("VARIANTS", "trait");

        for( VariantInfo obj: dao.getVariantsBySource("CLINVAR") ) {
            this.index(obj.getRgdId(), obj.getObjectType(), "VARIANTS", "type", obj.getSpeciesTypeKey());
            Term term = ontologyXDAO.getTermByAccId(obj.getSoAccId());
            if( term!=null )
                this.index(obj.getRgdId(), term.getTerm(), "VARIANTS", "type", obj.getSpeciesTypeKey());
            this.index(obj.getRgdId(), obj.getSymbol(), "VARIANTS", "symbol", obj.getSpeciesTypeKey());
            this.index(obj.getRgdId(), obj.getName(), "VARIANTS", "name", obj.getSpeciesTypeKey());
            this.index(obj.getRgdId(), obj.getTraitName(), "VARIANTS", "trait", obj.getSpeciesTypeKey());
            objsIndexed++;
        }

        indexAliases(RgdId.OBJECT_KEY_VARIANTS, "VARIANTS");

        return objsIndexed;
    }

    public int deleteFromIndex(String objectType, String dataType) throws Exception {
        if( isCanDeleteFromIndex() ) {
            return searchDAO.deleteFromIndex(objectType, dataType, getDeleteBatchSize());
        } else
            return 0;
    }

    public void indexTest() {
        System.out.println("ran SearchIndexerTest");
    }


    public void setDeleteBatchSize(int deleteBatchSize) {
        this.deleteBatchSize = deleteBatchSize;
    }

    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }
}
