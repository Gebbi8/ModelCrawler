package de.unirostock.sems.ModelCrawler.databases.localDirectory;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.unirostock.sems.ModelCrawler.databases.BioModelsDb.BioModelsChange;
import de.unirostock.sems.ModelCrawler.databases.Interface.Change;

public class LocalDirectoryChange extends Change {

	private final Log log = LogFactory.getLog( BioModelsChange.class );

	public final static String HASH_ALGORITHM = "SHA-256";
	public final static String HASH_ALGORITHM_FALLBACK = "SHA";
	
	public final static String META_HASH = "filehash";
	
	
	/**
	 * 
	 * @param localRepository path to the root of directory hosting the files
	 * @param filePath relative (TODO relative?) path to the file in the repo
	 * @param versionId identifier for the version of this model
	 * @param versionDate date of the version
	 * @param crawledDate when obtained?
	 * @throws URISyntaxException
	 */
	public LocalDirectoryChange( URL localRepository, String filePath, String versionId, Date versionDate, Date crawledDate ) throws URISyntaxException {
		super( localRepository, new String(FilenameUtils.getBaseName(filePath) + "." + FilenameUtils.getExtension(filePath)), versionId, versionDate, crawledDate );
		//super( repositoryUrl, filePath, versionId, versionDate, crawledDate );
	}
	
	
}
