package de.unirostock.sems.ModelCrawler;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.unirostock.sems.ModelCrawler.Config.WorkingMode;
import de.unirostock.sems.ModelCrawler.databases.BioModelsDb.BioModelsDb;
import de.unirostock.sems.ModelCrawler.databases.Interface.Change;
import de.unirostock.sems.ModelCrawler.databases.Interface.ChangeSet;
import de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase;
import de.unirostock.sems.ModelCrawler.databases.PMR2.PmrDb;
import de.unirostock.sems.ModelCrawler.databases.localDirectory.LocalDirectory;
import de.unirostock.sems.ModelCrawler.exceptions.ConfigurationException;
import de.unirostock.sems.ModelCrawler.exceptions.StorageException;
import de.unirostock.sems.ModelCrawler.storage.FileStorage;
import de.unirostock.sems.ModelCrawler.storage.ModelStorage;
import de.unirostock.sems.morre.client.MorreCrawlerInterface;
import de.unirostock.sems.morre.client.exception.MorreException;
import de.unirostock.sems.morre.client.impl.HttpMorreClient;

public class App {
	private static App instance = null;

	private static final Log log = LogFactory.getLog( App.class );
	
	private static MorreCrawlerInterface morreClient;
	private static ModelStorage storage = null;
	private static Map<String, ChangeSet> changes = null;
	private static Map<String, ChangeSet> changesPerRelease = null;
	
	
	public static void main(String [] args) {
		getApp(args);
	}
	
	
	// avoid creating more than one crawler
	public static App getApp(String[] args) {
		if( instance == null ){
			instance = new App(args);
		}
		return instance;
	}
	
	//public static void main( String[] args ) {
	/* create a crawler from the provided configuration
	 * and crawls the provided datasets
	 */
	private App ( String[] args ) {
		
		File configFile = configureCrawler(args); // TODO: cleanup the Config object, then isolate the config-related stuff in one function so to deal with the different operation modes there
		
		log.info("ModelCrawler started");
		
		// before-crawling operations for mode TEMPLATE_CONFIG
		if( Config.getWorkingMode() == WorkingMode.TEMPLATE_CONFIG ) {
			if( configFile == null ) {
				log.error("No config file provided, use -c flag");
				System.exit(0);
			}
			
			log.info( MessageFormat.format("Writing default config to {0}", configFile) );
			
			Config config = Config.defaultConfig();
			
			config.getDatabases().add( new BioModelsDb() );
			config.getDatabases().add( new PmrDb() );
			//added for LD
			config.getDatabases().add( new LocalDirectory() );
			config.setStorage( new FileStorage() );
			
			try {
				Config.getConfig().save(configFile);
			} catch (ConfigurationException e) {
				log.fatal( MessageFormat.format("Can not save config file {0}", configFile), e );
			}
			
			log.info("done.");
			System.exit(0);
		}
		
		// get the provided configuration from the configuration file
		try {
			if( configFile != null )
				Config.load( configFile );
		} catch (ConfigurationException e) {
			log.fatal( MessageFormat.format("Can not load config file {0}", configFile), e );
		}
		Config config = Config.getConfig();
		
		// Connectors
		initConnectors( config );
		
		// map for all changes!
		// Map<String, ChangeSet> changes = new HashMap<String, ChangeSet>();
		changes = new HashMap<String, ChangeSet>();

		// run it!
		for( ModelDatabase database : config.getDatabases() ) {
			
			if( database.isEnabled() == false )
				continue;
			
			if( log.isInfoEnabled() )
				log.info( MessageFormat.format("running crawler for {0}", database.getClass().getName()) );
			
			changesPerRelease = database.call();
			
			// add all changes to the change Map
			changes.putAll( database.listChanges() );
			
			if( log.isInfoEnabled() )
				log.info( MessageFormat.format("finished crawling for {0}", database.getClass().getName()) );
		}
		
		int changeSetCount = 0;
		int modelCount = 0;
		
		int changeSetCountNoMorre = 0;
		int modelCountNoMorre = 0;
		
		// after-crawling operations for mode TEST
		if( Config.getWorkingMode() == WorkingMode.TEST ) {
			log.info("Do not push ChangeSets to morre or store them in test-mode");
		}
		else if( Config.getWorkingMode() == WorkingMode.NO_MORRE ) {
			
			log.info("Do not push ChangeSets to morre in NO_MORRE mode.");
	    	Iterator<ChangeSet> changesSetIterator = changes.values().iterator();
	    	while( changesSetIterator.hasNext() ) {
	    		// ... and process them
	    		int count = processChangeSet( changesSetIterator.next() );
	    		System.out.println(count);
	    		System.out.println("test");
	    		changeSetCountNoMorre++;
	    		modelCountNoMorre = modelCountNoMorre + count;
	    	}
			
		}
		else {
			
	    	Iterator<ChangeSet> changesSetIterator = changes.values().iterator();
	    	while( changesSetIterator.hasNext() ) {
	    		// ... and process them
	    		int count = processChangeSet( changesSetIterator.next() );
	    		changeSetCount++;
	    		modelCount = modelCount + count;
	    	}
	    	
	    	storage.close();
		}

		// After everything is done: Hide the bodies...
		close();

		log.info("finished crawling");
		log.info( MessageFormat.format("pushed {0} changesets with {1} models to morre, {2,number,##.#} models per changeset", changeSetCount, modelCount, (double) (changeSetCount > 0 ? modelCount/changeSetCount : 0.0) ));
		log.info( MessageFormat.format("found {0} changesets with {1} models, {2,number,##.#} models per changeset", changeSetCountNoMorre, modelCountNoMorre, (double) (changeSetCountNoMorre > 0 ? modelCountNoMorre/changeSetCountNoMorre : 0.0) ));
	}
	
	
	/* configure the crawler
	 * 
	 */
	private static File configureCrawler(String[] args){
		File configFile = null;
		
		// validate the provided configuration
		if( args.length == 0 ) {
			printHelp();
			System.exit(0);
		}

		// set the working mode
		for( int index = 0; index < args.length; index++ ) {
			
			if( args[index].equals("-c") || args[index].equals("--config") )
				configFile = new File(args[++index]);
			else if( args[index].equals("--template") )
				Config.setWorkingMode( WorkingMode.TEMPLATE_CONFIG );
			else if( args[index].equals("--test") )
				Config.setWorkingMode( WorkingMode.TEST );
			else if( args[index].equals("--no-morre") )
				Config.setWorkingMode( WorkingMode.NO_MORRE );
		}
		
		return configFile;
	}

	
	private static void printHelp() {
		System.out.println("ModelCrawler");
		System.out.println(
				"  -c               Path to config\n" + 
				"  --config \n" +
				"  --template       Writes down a template config file (overrides existing config!) \n" +
				"  --test           Test mode. Nothing is pushed to morre nor stored persistent \n" +
				"  --no-morre       Do not utilize morre to determine the latest known version nor \n" +
				"                   stores any model in the database. Just download and store models. \n" +
				"                   May cause doubles, when used for BioModels"
		);
	}

	private static void initConnectors(Config config) {

		// Storage
		log.info("Prepare storage connector");
		storage = config.getStorage();
		try {
			storage.connect();
		} catch (StorageException e) {
			log.fatal("Exception while connecting to storage", e);
			return;
		}
		
		// morre.client connector
		if( log.isInfoEnabled() )
			log.info("Start GraphDb/MORRE connector");
		
		try {
			if( Config.getWorkingMode() != WorkingMode.NO_MORRE )
				morreClient = new HttpMorreClient( config.getMorreUrl() );
		} catch (MalformedURLException e) {
			log.fatal("Malformed Url for MORRE in config file", e);
		}
		
		// setting morre and storage for each database connector
		for( ModelDatabase connector : config.getDatabases() ) {
			connector.setMorreClient(morreClient);
			connector.setModelStorage(storage);
		}
		
	}

	private static void close() {
		log.info("Cleans everything up!");
		
		// closing every database connector
		for( ModelDatabase database : Config.getConfig().getDatabases() ) {
			if( database.isEnabled() == false )
				continue;
			
			database.close();
		}
	}

	private static int processChangeSet( ChangeSet changeSet ) {
		
		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("Start processing ChangeSet for model {0} with {1} entrie(s)", changeSet.getFileId(), changeSet.getChanges().size() ) );
		
		int modelCount = 0;
		Iterator<Change> changeIterator = changeSet.getChanges().iterator();
		Change change = null;
		try {
			while( changeIterator.hasNext() ) {
				change = (Change) changeIterator.next();

				if( log.isInfoEnabled() )
					log.info( MessageFormat.format("pushes model {0}:{1}", change.getFileId(), change.getVersionId()) );

				// store the model, only if not already stored
				String modelUri = change.getXmldoc();
				if( modelUri == null )
					modelUri = storage.storeModel(change).toString();
				
				change.setXmldoc( modelUri );
				
				// insert the model into MaSyMos via Morre
				morreClient.addModel(change);
				modelCount++;
			}
		} catch (IOException e) {
			log.fatal( MessageFormat.format("Some IO stuff went wrong while pushing model {0} !", change), e);
		} catch (MorreException e) {
			log.error( MessageFormat.format("Morre encountered an error while puschin model {0} : {1}", change, e.getMessage()), e);
		} catch (StorageException e) {
			log.fatal( MessageFormat.format("Something went wrong while pushing model {0} !", change), e);
		}

		return modelCount;
	}
	
	public Map<String, ChangeSet> getChanges(){
		return changes;
	}
	
	public Map<String, ChangeSet> getChangesPerRelease(){
		return changesPerRelease;
	}
	
}
