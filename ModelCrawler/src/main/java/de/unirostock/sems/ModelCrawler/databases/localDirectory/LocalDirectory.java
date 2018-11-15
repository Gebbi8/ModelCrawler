/**
 * 
 */
package de.unirostock.sems.ModelCrawler.databases.localDirectory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.unirostock.sems.ModelCrawler.Config;
import de.unirostock.sems.ModelCrawler.Config.WorkingMode;
import de.unirostock.sems.ModelCrawler.databases.Interface.ChangeSet;
import de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase;
import de.unirostock.sems.ModelCrawler.databases.PMR2.PmrDb;
import de.unirostock.sems.ModelCrawler.databases.PMR2.PmrDb.WorkingDirConfig;
import de.unirostock.sems.bives.tools.DocumentClassifier;

/**
 * @author tgeb
 *
 */
public class LocalDirectory extends ModelDatabase {
	
	
	protected String rootDir;
	protected boolean inverse;
	protected File workingDir;
	protected boolean enabled;
	protected int limit;
	
	@JsonIgnore
	private final Log log = LogFactory.getLog( LocalDirectory.class );
	
	@JsonIgnore
	protected DocumentClassifier classifier = null;
	
	@JsonIgnore
	protected WorkingDirConfig config = null;
	
	@JsonIgnore
	protected Set<String> fileExtensionBlacklist = new HashSet<String>();
	

	/* (non-Javadoc)
	 * @see de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase#listModels()
	 */
	@Override
	public List<String> listModels() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase#listChanges()
	 */
	@Override
	public Map<String, ChangeSet> listChanges() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase#getModelChanges(java.lang.String)
	 */
	@Override
	public ChangeSet getModelChanges(String fileId) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase#close()
	 */
	@Override
	public void close() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase#call()
	 */
	@Override
	public Map<String, ChangeSet> call() {
		// TODO Auto-generated method stub
		
		if( morreClient != null || Config.getWorkingMode() != WorkingMode.NO_MORRE ) {
			log.error("Not able to do Morre");
			throw new IllegalArgumentException("Not able to do Morre");
		}
		
		// init Document classifier
		classifier = new DocumentClassifier ();

		if( log.isInfoEnabled() )
			log.info("Started BiVeS Classifier");

		// Prepare WorkingDir 
		init();
		log.info("Start crawling the PMR2 Database by going throw the Mercurial Workspaces");
		
		

		// getting the current Date, for crawling TimeStamp
		Date crawledDate = new Date();
		

		change = new BioModelsChange(repositoryUrl, filePath, release.getReleaseName(), release.getReleaseDate(), crawledDate);
		change.setMeta(CrawledModelRecord.META_SOURCE, CrawledModelRecord.SOURCE_BIOMODELS_DB);
		change.setModelType( CrawledModelRecord.TYPE_SBML );
		change.setXmlFile( release.getModelPath(fileName) );
		is new?
				
		change.addParent( latest.getFileId(), latest.getVersionId() );
		URI modelUri = modelStorage.storeModel(change);
		change.setXmldoc( modelUri.toString() );

		return null;
	}
	
	protected void init() {

		workingDir = obtainWorkingDir();
		log.trace( "Preparing working dir " + workingDir.getAbsolutePath() );

		if( workingDir.exists() == false ) {
			// creates it!
			workingDir.mkdirs();
		}
		// create temp dir
		createTempDir();

	/*	try {
			// inits the config
			log.info("Loading working dir config");
			File configFile = new File( workingDir, Config.getConfig().getWorkingDirConfig() );
			if( configFile.exists() )
				config = Config.getObjectMapper().readValue( configFile, WorkingDirConfig.class );
			else
				config = new WorkingDirConfig();
		}
		catch (IOException e) {
			log.fatal( "IOException while reading the workingdir config file", e );
		}

	*/	// fill own copy of blacklist
		fileExtensionBlacklist.addAll(
				Arrays.asList( Config.getConfig().getExtensionBlacklist() )
				);
	}

}
