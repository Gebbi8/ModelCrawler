package de.unirostock.sems.ModelCrawler.databases.localDirectory;

import java.io.File;
import java.util.Date;
import java.util.Map;

//Do we have releases? probably different  version folders

public class LocalDirectoryRelease {
	
	private String releaseName;
	private String localDirectory;
	private Date releaseDate;
	
	private Map<String, File> modelMap;
	
	public String getReleaseName() {
		return releaseName;
	}
	public Date getReleaseDate() {
		return releaseDate;
	}
	
	public String getLocalDirectory() {
		return localDirectory;
	}
	
	public File getModelPath( String fileId ) {
		return modelMap.get(fileId);
	}
}
