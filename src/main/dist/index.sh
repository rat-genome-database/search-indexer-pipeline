#index all groups of objects
. /etc/profile
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
APPDIR=/home/rgddata/pipelines/SearchIndexer
EMAIL_LIST=mtutaj@mcw.edu,jthota@mcw.edu
if [ "$SERVER" = "REED" ]; then
  EMAIL_LIST=mtutaj@mcw.edu,jthota@mcw.edu,jdepons@mcw.edu,szacher@mcw.edu
fi

cd $APPDIR

./_run.sh \
  Truncate \
  GenomicElementSymbols \
  GeneSymbols \
  SSLPSymbols \
  QTLSymbols \
  StrainSymbols \
  GeneNames \
  QTLNames \
  StrainNames \
  StrainOrigins \
  QTLSubTraits \
  QTLTraits \
  Variants \
  OntologyTerms \
  OntologySynonyms \
  OntologyDefinitions \
  Aliases \
  GeneDescriptions \
  GeneTranscripts \
  ReferenceTitles \
  ReferenceCitations \
  ReferenceAuthors \
  ReferencePubYears \
  ExternalIdentifiers \
  2>&1 | tee index.log

mailx -s "[$SERVER] Search Indexing Complete" $EMAIL_LIST < index.log

