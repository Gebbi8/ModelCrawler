package de.unirostock.sems.ModelCrawler.databases.Interface;

import java.io.Closeable;
import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.unirostock.sems.ModelCrawler.Config;
import de.unirostock.sems.ModelCrawler.Constants;
import de.unirostock.sems.ModelCrawler.databases.BioModelsDb.BioModelsDb;
import de.unirostock.sems.ModelCrawler.databases.PMR2.PmrDb;
import de.unirostock.sems.ModelCrawler.databases.localDirectory.LocalDirectory;
import de.unirostock.sems.ModelCrawler.storage.ModelStorage;
import de.unirostock.sems.morre.client.MorreCrawlerInterface;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonTypeInfo(
		use = JsonTypeInfo.Id.NAME,
		include = JsonTypeInfo.As.PROPERTY,
		property = "type" )
@JsonSubTypes({
	@Type( value = ModelDatabase.class, name = ModelDatabase.DatabaseTypes.NONE ),
	@Type( value = LocalDirectory.class, name = ModelDatabase.DatabaseTypes.LD ),
	@Type( value = BioModelsDb.class, name = ModelDatabase.DatabaseTypes.BMDB ),
	@Type( value = PmrDb.class, name = ModelDatabase.DatabaseTypes.PMR2 )
})
public abstract class ModelDatabase implements Callable<Map<String, ChangeSet>>, Closeable, Serializable {
	
	private static final long serialVersionUID = -3406749011901702763L;
	
	//public static Map<String, String> release_maps = null;

	public abstract class DatabaseTypes {
		public static final String NONE = "";
		public static final String LD = "LD";
		public static final String BMDB = "BMDB";
		public static final String PMR2 = "PMR2";
	}
	
	@JsonIgnore
	private final Log log = LogFactory.getLog( ModelDatabase.class );
	
	protected boolean enabled = false;
	private String workingDirName = "wd-" + String.valueOf( Math.abs(new Random(new Date().getTime()).nextLong()) );
	private File workingDir = null;
	protected int limit = 0;
	
	@JsonIgnore
	protected File tempDir = null;
	@JsonIgnore
	protected MorreCrawlerInterface morreClient = null;
	@JsonIgnore
	protected ModelStorage modelStorage = null;
	
	public ModelDatabase() {}		
	
	/**
	 * creates a new and empty temporary directory and sets the class variable.
	 *
	 * @return the temporary directory object
	 */
	protected synchronized File createTempDir() {
		
		// create temp dir
		Config config = Config.getConfig();
		try {
			tempDir = Files.createTempDirectory( config.getTempDir().toPath(), config.getTempDirPrefix(), Constants.TEMP_DIR_POSIX_ATTRIBUTES ).toFile();
			tempDir.deleteOnExit();
		} catch (Exception e) {
			tempDir = new File( config.getTempDir(), String.valueOf(new Random( new Date().getTime() ).nextLong()) );
			tempDir.mkdirs();
			tempDir.deleteOnExit();
			
			log.error("Cannot create TempDirectory, using '" + tempDir.getAbsolutePath() + "' instead.", e);
		}
		
		return tempDir;
	}
	
	/**
	 * lists all Models in the latest revision
	 * 
	 * @return List with all model IDs
	 */
	@JsonIgnore
	public abstract List<String> listModels();
	
	/**
	 * Returns a map with all changes made after the last crawl <br>
	 * the fileId is the map key.
	 * 
	 * @return the list of changes
	 */
	@JsonIgnore
	public abstract Map<String, ChangeSet> listChanges();
	
	/**
	 * Returns the ChangeSet only for one specific model.
	 *
	 * @param fileId the file id
	 * @return ChangeSet
	 */
	@JsonIgnore
	public abstract ChangeSet getModelChanges( String fileId );
	
	/**
	 * Cleans up the working directory
	 */
	@JsonIgnore
	public abstract void close();
	
	/**
	 * Starts the process of crawling for this specific Database.
	 *
	 * @return the map of files and changes
	 */
	@JsonIgnore
	public abstract Map<String, ChangeSet> call();

	public String getWorkingDir() {
		return this.workingDirName;
	}
	
	protected synchronized File obtainWorkingDir() {
		if( workingDir == null )
			workingDir = new File( Config.getConfig().getWorkingDir(), workingDirName );
		
		return workingDir;
	}

	public void setWorkingDir(String workingDir) {
		this.workingDirName = workingDir;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}
	
	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@JsonIgnore
	public MorreCrawlerInterface getMorreClient() {
		return morreClient;
	}
	
	@JsonIgnore
	public void setMorreClient(MorreCrawlerInterface morreClient) {
		this.morreClient = morreClient;
	}
	
	@JsonIgnore
	public ModelStorage getModelStorage() {
		return modelStorage;
	}
	
	@JsonIgnore
	public void setModelStorage(ModelStorage modelStorage) {
		this.modelStorage = modelStorage;
	}
	
}