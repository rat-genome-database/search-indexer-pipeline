#index genomic elements: CELL_LINES and PROMOTERS
. /etc/profile
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
cd /home/rgddata/pipelines/SearchIndexer

./_run.sh GenomicElementSymbols 2>&1 | tee index_ge.log

EMAIL_LIST=mtutaj@mcw.edu,jthota@mcw.edu
if [ "$SERVER" = "REED" ]; then
  EMAIL_LIST=mtutaj@mcw.edu,jthota@mcw.edu,jdepons@mcw.edu,szacher@mcw.edu
fi

mailx -s "[$SERVER] Search Indexing Complete For Genomic Elements" $EMAIL_LIST < index_ge.log

