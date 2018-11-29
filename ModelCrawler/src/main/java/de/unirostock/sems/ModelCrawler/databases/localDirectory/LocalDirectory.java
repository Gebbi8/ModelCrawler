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
import java.util.Objects;
import java.util.Set;
import java.util.Date;
import java.util.HashMap;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.sparql.function.library.leviathan.root;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


import de.unirostock.sems.ModelCrawler.Config;
import de.unirostock.sems.ModelCrawler.Config.WorkingMode;
import de.unirostock.sems.ModelCrawler.databases.Interface.ChangeSet;
import de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase;
import de.unirostock.sems.ModelCrawler.exceptions.StorageException;
import de.unirostock.sems.bives.tools.DocumentClassifier;
import de.unirostock.sems.ModelCrawler.helper.CrawledModelRecord;
import de.unirostock.sems.ModelCrawler.storage.ModelStorage;


/**
 * @author tgeb
 *
 */
public class LocalDirectory extends ModelDatabase {
	
	
	protected String root;
	protected boolean inverse;
	protected File workingDir;
	protected boolean enabled;
	protected int limit;
	protected URL repoListUrl;
	protected String fileStructure;
	

	public String getFileStructure() {
		return fileStructure;
	}

	public void setFileStructure (String fileStructure) {
		this.fileStructure = fileStructure;
	}
	
	public boolean getInverse() {
		return inverse;
	}
	
	public void setInverse (boolean inverse) {
		this.inverse = inverse;
	}

	public void setRoot (String root) {
		this.root = root;
	}
	
	public String getRoot() {
		return root;
	}
	
	public URL getRepoListUrl() {
		return repoListUrl;
	}

	public void setRepoListUrl(URL repoListUrl) {
		this.repoListUrl = repoListUrl;
	}
	
	Map<String, ChangeSet> changeSetMap = new HashMap<String, ChangeSet>();
	
	@JsonIgnore
	private final Log log = LogFactory.getLog( LocalDirectory.class );
	@JsonIgnore
	private URL repoUrl;
	
	@JsonIgnore
	protected DocumentClassifier classifier = null;
	
	@JsonIgnore
	protected WorkingDirConfig config = null;
	
	@JsonIgnore
	protected Set<String> fileExtensionBlacklist = new HashSet<String>();
	

	private static class WorkingDirConfig {
		
		private HashSet<String> knownReleases = new HashSet<String>();
		
		public HashSet<String> getKnownReleases() {
			return knownReleases;
		}

		@SuppressWarnings("unused")
		public void setKnownReleases(HashSet<String> knownReleases) {
			this.knownReleases = knownReleases;
		}
		
	}
	
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
		return changeSetMap;
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
		
		File rootDir = new File (root);
		try {
			repoUrl = rootDir.toURI().toURL();
		} catch (MalformedURLException e2) {
			// TODO Auto-generated catch block
			log.error("Not able to do Morre");
;
			e2.printStackTrace();
		}
		
		
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
		log.info("Start crawling the local repo by going throw the directory " + rootDir);
		
	
		
	//	System.out.println(rootDir);



		// Create a Pattern object
		Pattern regex = Pattern.compile(fileStructure);
		
		
		findModels(regex, rootDir, rootDir);
		
		return changeSetMap;
		

	}
	
	protected boolean findModels(Pattern regex, File dir, File rootDir) {
		
		for(File i : dir.listFiles()) {
			// Now create matcher object.
			Matcher m = regex.matcher(i.getAbsolutePath());
			if (m.find( )) {
				
				produceChange(rootDir, i, m.group(1), m.group(2) );
			}else {

				if(i.isDirectory())
					findModels(regex, i, rootDir);
			}
		}


		return false;   		
	}
	
	protected boolean produceChange(File rootDir, File modelVersion, String topId, String btmId) {
		
		LocalDirectoryChange change = null;
		
		int type = classifier.classify(modelVersion);

		
		if( (type & DocumentClassifier.XML) == 0 || ((type & DocumentClassifier.SBML) == 0 && (type & DocumentClassifier.CELLML) == 0) )
			return false;
		
		//get relative file path from absolute path and root directory path
		String filePath = rootDir.toURI().relativize(modelVersion.toURI()).getPath();
		//get changeSet from changeSetMap by path
		LocalDirectoryChangeSet changeSet = (LocalDirectoryChangeSet) changeSetMap.get(filePath);
		//if no changeSet was found create new one and add it to changeSetMap
		if (changeSet == null) {
			changeSet = new LocalDirectoryChangeSet(repoUrl, filePath);
			changeSetMap.put(filePath, changeSet);
		}
		
		


		try {
			
			String modelId;
			String versionId;	
			
			Date crawledDate = new Date();
			
			if(inverse) {
				modelId = btmId;//modelVersion.getName();
				versionId = topId;//model.getName();
			} else {
				modelId = topId;//model.getName();
				versionId = btmId;//modelVersion.getName();
			}
			
			Date versionDate = new Date (modelVersion.lastModified());//Files.readAttributes(modelVersion.toPath(), BasicFileAttributes.class).creationTime();
			
			change = new LocalDirectoryChange(repoUrl, versionId, modelId, versionDate, crawledDate);
			

			change.setMeta(CrawledModelRecord.META_SOURCE, CrawledModelRecord.SOURCE_LOCAL_DIR); //cant add new source to the crawledModel
			
			
			//set model type according to classiefier
			if ((type & DocumentClassifier.SBML) > 0) {
				change.setModelType( CrawledModelRecord.TYPE_SBML );
			} else if ((type & DocumentClassifier.CELLML) > 0) {
				change.setModelType( CrawledModelRecord.TYPE_CELLML );
			} else {
				if( log.isInfoEnabled() )
					log.info(MessageFormat.format("file is not a valid model document {0} ", modelVersion));
				return false;
			}
			
			 //set path to file by absolute path
			change.setXmlFile( modelVersion );
			
			// puts change in model
			try {
				URI modelUri = modelStorage.storeModel(change);
				change.setXmldoc( modelUri.toString() );
			} catch (StorageException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
		} catch (URISyntaxException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		return true;
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
