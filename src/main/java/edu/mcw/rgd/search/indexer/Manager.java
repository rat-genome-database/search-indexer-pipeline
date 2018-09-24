package edu.mcw.rgd.search.indexer;

import edu.mcw.rgd.process.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Apr 12, 2011
 * Time: 5:51:46 PM
 */
public class Manager {

    Log log = LogFactory.getLog(Manager.class);
    private String version;
    private Spider spider;

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        Manager instance = (Manager) (bf.getBean("manager"));

        try {
            instance.run(args);
        } catch(Exception e) {
            Utils.printStackTrace(e, instance.log);
            e.printStackTrace();
            throw e;
        }
    }

    void run(String[] args) throws Exception {
        
        log.info(getVersion());

        if (args.length == 0) {
            log.info("Indexing Options:");
            log.info("\tExternalIdentifiers");
            log.info("\tAliases");
            log.info("\tQTLSubTraits");
            log.info("\tQTLTraits");
            log.info("\tStrainSymbols");
            log.info("\tStrainNames");
            log.info("\tStrainOrigins");
            log.info("\tStrainSources");
            log.info("\tStrainTypes");
            log.info("\tReferenceTitles");
            log.info("\tReferenceCitations");
            log.info("\tReferenceAuthors");
            log.info("\tReferencePubYears");
            log.info("\tSSLPSymbols");
            log.info("\tQTLNames");
            log.info("\tQTLSymbols");
            log.info("\tGeneNames");
            log.info("\tGeneSymbols");
            log.info("\tGeneDescriptions");
            log.info("\tGeneTranscripts");
            log.info("\tOntologyTerms");
            log.info("\tOntologySynonyms");
            log.info("\tOntologyDefinitions");
            log.info("\tGenomicElementSymbols");
            log.info("\tVariants");
            log.info("\n\n");
            log.info("Other Options:");
            log.info("\tTruncate");
            log.info("\tDebug");
            return;

        }

        long start = System.currentTimeMillis();

        boolean canDisableIndexes = false;
        String msg = "Attempting to index: ";
        for (String arg: args) {
            msg += arg + " ";
            if (arg.equals("Truncate")) {
                canDisableIndexes = true;
            }
        }
        log.info(msg);

        if( canDisableIndexes ) {
            // if TRUNCATE is called on index, there is no need to get rid of previous data
            // say information for gene symbols
            spider.setCanDeleteFromIndex(false);

            disableIndexes();
        }

        for (String arg: args) {
            if (arg.equals("Truncate")) {
                log.info("Truncating Index");
                spider.truncateIndex();
            }
            else {
                log.info("Indexing " + arg);
                long time1 = System.currentTimeMillis();
                spider.getClass().getMethod("index" + arg).invoke(spider);
                long time2 = System.currentTimeMillis();
                log.info(" - " + Utils.formatElapsedTime(time1, time2));
            }
        }

        spider.finish();

        if( canDisableIndexes ) {
            enableIndexes();
        }

        long stop = System.currentTimeMillis();
        log.info("Time elapsed - " + Utils.formatElapsedTime(start, stop));
        log.info("Finished at: " + (new Date()));
    }
    
    void disableIndexes() throws Exception {

        long time1 = System.currentTimeMillis();
        log.info("  disabling indexes for table RGD_INDEX ");
        spider.disableIndexes();
        log.info("  indexes for table RGD_INDEX disabled: " + Utils.formatElapsedTime(time1, System.currentTimeMillis()));
    }

    void enableIndexes() throws Exception {

        log.info("");
        log.info("  enabling indexes for table RGD_INDEX ");
        long time1 = System.currentTimeMillis();
        spider.enableIndexes();
        log.info("  indexes for table RGD_INDEX enabled: " + Utils.formatElapsedTime(time1, System.currentTimeMillis()));
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setSpider(Spider spider) {
        this.spider = spider;
    }

    public Spider getSpider() {
        return spider;
    }
}
