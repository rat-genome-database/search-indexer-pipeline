# build search index for public ontologies
. /etc/profile
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
APPDIR=/home/rgddata/pipelines/SearchIndexer
EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" = "REED" ]; then
  EMAIL_LIST=mtutaj@mcw.edu,jdepons@mcw.edu,szacher@mcw.edu
fi

cd $APPDIR

./_run.sh OntologyTerms OntologySynonyms OntologyDefinitions 2>&1 | tee index_ont.log

mailx -s "[$SERVER] Search Indexing Complete For Ontologies" $EMAIL_LIST < index_ont.log

