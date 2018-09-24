package edu.mcw.rgd.test;

import edu.mcw.rgd.search.indexer.Spider;
import junit.framework.TestCase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Apr 14, 2011
 * Time: 8:44:55 AM
 */
public class SearchIndexerTest extends TestCase {

    public SearchIndexerTest(String testName) {
        super(testName);
    }

    public void testIndexGeneSymbols() throws Exception{
        Spider sdao = new Spider();
        sdao.indexGeneSymbols();

    }

    public void testIndexGeneNames() throws Exception{
        Spider sdao = new Spider();
        sdao.indexGeneNames();
    }

    public void testIndexGeneDescriptions() throws Exception{
        Spider sdao = new Spider();
        sdao.indexGeneDescriptions();

    }

    public void testIndexQTLSymbols() throws Exception{
        Spider sdao = new Spider();
        sdao.indexQTLSymbols();
    }

    public void testIndexReferenceCitations() throws Exception{
        Spider sdao = new Spider();
        sdao.indexReferenceCitations();
    }

    public void testIndexAll() throws Exception{
        Spider sdao = new Spider();

        SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");

        System.out.println("Starting at: " + format.format(new Date()));

        sdao.indexGenomicElementSymbols();
        System.out.println("finished genomic element symbols " + format.format(new Date()));

        sdao.indexOntologyTerms();
        System.out.println("finished ontology terms " + format.format(new Date()));
        sdao.indexOntologySynonyms();
        System.out.println("finished ontology synonyms " + format.format(new Date()));
        sdao.indexOntologyDefinitions();
        System.out.println("finished ontology definitions " + format.format(new Date()));

        sdao.indexGeneDescriptions();
        System.out.println("finished gene descriptions " + format.format(new Date()));
        sdao.indexGeneSymbols();
        System.out.println("finished gene symbols" + format.format(new Date()));
        sdao.indexGeneNames();
        System.out.println("finished gene names " + format.format(new Date()));

        sdao.indexQTLNames();
        System.out.println("finished qtl names " + format.format(new Date()));
        sdao.indexQTLSymbols();
        System.out.println("finished qtl symbols " + format.format(new Date()));
        sdao.indexSSLPSymbols();
        System.out.println("finished sslp symbols " + format.format(new Date()));

        sdao.indexReferenceCitations();
        System.out.println("finished reference citations " + format.format(new Date()));

        sdao.indexReferenceTitles();
        System.out.println("finished reference titles " + format.format(new Date()));

        sdao.indexStrainNames();
        System.out.println("finished strain names " + format.format(new Date()));
        sdao.indexStrainSymbols();
        System.out.println("finished strain symbols - DONE" + format.format(new Date()));
        sdao.indexQTLTraits();
        System.out.println("finished qtl traits " + format.format(new Date()));
        sdao.indexQTLSubTraits();
        System.out.println("finished qtl sub traits " + format.format(new Date()));
        sdao.indexAliases();
        System.out.println("finished aliases " + format.format(new Date()));

        sdao.indexExternalIdentifiers();
        System.out.println("finished externalIdentifiers " + format.format(new Date()));

        sdao.finish();
    }

    public void testRegEx() throws Exception {

        Pattern pattern = Pattern.compile("[a-zA-Z_0-9\\-]+");
        Matcher matcher = pattern.matcher("hello-all we, are");

        while (matcher.find()) {
        // Get all groups for this match
            for (int i=0; i<=matcher.groupCount(); i++) {
                String groupStr = matcher.group(i);
                System.out.println(groupStr);
            }
        }
    }
}
