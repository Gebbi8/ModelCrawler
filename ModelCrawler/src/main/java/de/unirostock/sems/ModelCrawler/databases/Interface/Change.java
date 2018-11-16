package de.unirostock.sems.ModelCrawler.databases.Interface;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;

import org.apache.commons.io.FilenameUtils;

import de.unirostock.sems.ModelCrawler.Config;
import de.unirostock.sems.ModelCrawler.Constants;
import de.unirostock.sems.ModelCrawler.helper.CrawledModelRecord;


/**
 * specific change on a model file
 * 
 * basically representing a model version...?
 * 
 * @author tgeb
 *
 */
public abstract class Change extends CrawledModelRecord implements Comparable<Change> {
	
	private static final long serialVersionUID = 3880353134783045794L;
	private transient Date versionDate = null;
	private transient Date crawledDate = null;
	
	protected transient File xmlFile = null;
	protected transient URL repositoryUrl = null;
	
	protected transient String filePath = null;
	
	protected String fileName = null;
	protected String versionId = null;
	

	//public Change( URL repositoryUrl, String fileName, String versionId, Date versionDate, Date crawledDate ) throws URISyntaxException {
	/**
	 * 
	 * @param repositoryUrl 
	 * @param filePath 
	 * @param versionId 
	 * @param versionDate 
	 * @param crawledDate 
	 * @throws URISyntaxException
	 */
	public Change( URL repositoryUrl, String filePath, String versionId, Date versionDate, Date crawledDate ) throws URISyntaxException {
		
		//super( generateFileId(repositoryUrl, fileName), versionId, versionDate, crawledDate );
    super( generateFileId(repositoryUrl, filePath), versionId, versionDate, crawledDate );
    //super( generateFileId(repositoryUrl, new String(FilenameUtils.getBaseName(filePath) + "." + FilenameUtils.getExtension(filePath))), versionId, versionDate, crawledDate );
		//super( generateFileId(repositoryUrl, filePath), versionId, versionDate, crawledDate );
	        
		// keep this meta-data on the retrieved change
		this.repositoryUrl = repositoryUrl;
		
		this.filePath = filePath;
		//System.err.println(" PATH " + this.filePath); System.exit(1);
		this.fileName = new String(FilenameUtils.getBaseName(filePath) + "." + FilenameUtils.getExtension(filePath));
		//this.fileName = fileName;
		
		this.versionId = versionId;
		this.versionDate = versionDate;
		this.crawledDate = crawledDate;
		/*
		System.err.println(" repository URL " + this.repositoryUrl);
		System.err.println(" file path " + this.filePath);
		System.err.println(" file name " + this.fileName);
		System.err.println(" version ID " + this.versionId);
		System.err.println(" version date " + this.versionDate);
		System.err.println(" crawled date " + this.crawledDate);
		System.exit(1);
		*/
	}
	
	
	
	public static String generateFileId( URL repositoryUrl, String filePath ) throws URISyntaxException {
		
		if( repositoryUrl == null )
			throw new IllegalArgumentException("Repository URL not provided");
		
		if( filePath == null || filePath.isEmpty() )
			throw new IllegalArgumentException("File path not provided");
		
		// gets config...
		Config config = Config.getConfig();
		
		// Build beginning of an urn "urn:"...
		StringBuilder fileId = new StringBuilder( Constants.URN_START );
		fileId.append( Constants.URN_SEPARATOR );
		
		// add namespace
		fileId.append( config.getUrnNamespace() );
		fileId.append( Constants.URN_SEPARATOR );
		
		// add host
		fileId.append(  repositoryUrl.getHost() );
		fileId.append( Constants.URN_SEPARATOR );
		
		// add path to repository (aka. workspace)
		String[] repositoryPathParts = repositoryUrl.getPath().split( Constants.PATH_SPLIT_REGEX );
		for( int index = 0; index < repositoryPathParts.length; index++ ) {
			
			if( repositoryPathParts[index] == null || repositoryPathParts[index].isEmpty() )
				continue;
			
			// path part starts with version placeholder -> remove the placeholder from the beginning
			if( repositoryPathParts[index].startsWith( Constants.URN_VERSION_PLACEHOLDER) ) {
				repositoryPathParts[index] = repositoryPathParts[index].substring( Constants.URN_VERSION_PLACEHOLDER.length() );
			}
			
			fileId.append( repositoryPathParts[index] );
			fileId.append( Constants.URN_SEPARATOR );
		}
		
		// add version placeholder
		fileId.append( Constants.URN_VERSION_PLACEHOLDER );
		fileId.append( Constants.URN_SEPARATOR );
		
		// add the file path (file location in the repository)
		String[] filePathParts = filePath.split( Constants.PATH_SPLIT_REGEX );
		for( int index = 0; index < filePathParts.length; index++ ) {
			
			if( filePathParts[index] == null || filePathParts[index].isEmpty() )
				continue;
			
			// path part starts with version placeholder -> remove the placeholder from the beginning
			if( filePathParts[index].startsWith( Constants.URN_VERSION_PLACEHOLDER) ) {
				filePathParts[index] = filePathParts[index].substring( Constants.URN_VERSION_PLACEHOLDER.length() );
			}
			
			fileId.append( filePathParts[index] );
			// don't add the URN Separator after the last part
			if( filePathParts.length - index > 1 )
				fileId.append( Constants.URN_SEPARATOR );
		}
		return fileId.toString();
	}
	
	public File getXmlFile() {
		return xmlFile;
	}
	
	public boolean setXmlFile( File xmlFile ) {
		return setXmlFile(xmlFile, false);
	}
	
	public boolean setXmlFile( File xmlFile, boolean override ) {
		//REMIND the xml file should only be setted once in a Change
		if( this.xmlFile == null || override == true) {
			this.xmlFile = xmlFile;
			return true;
		}
		else
			return false;
	}
	
	@Override
	public void setVersionDate(Date versionDate) {
		super.setVersionDate(versionDate);
		this.versionDate = versionDate;
	}
	
	@Override
	public Date getVersionDate() {
		if( versionDate == null )
			versionDate = super.getVersionDate();
		
		return versionDate;
	}
	
	@Override
	public void setCrawledDate(Date crawledDate) {
		super.setCrawledDate(crawledDate);
		this.crawledDate = crawledDate;
	}
	
	@Override
	public Date getCrawledDate() {
		if( crawledDate == null )
			crawledDate = super.getCrawledDate();
		
		return crawledDate;
	}
	
	@Override
	public int compareTo( Change change ) {
		return getVersionDate().compareTo( change.getVersionDate() );
	}
	
	@Override
	public String toString() {
		return "Chg:" + getFileId()+"@"+getVersionId();
	}
	
	
	/*
	 * get a change's repository URL
	 */
	public String getChangeRepositoryUrl(Change change){
		return change.repositoryUrl.toString();
	}
	/*
	 * get a change's file path
	 */
	public String getChangeFilePath(Change change){
		return change.filePath;
	}
	/*
	 * get a change's file name
	 */
	public String getChangeFileName(Change change){
		return change.fileName;
	}
	/*
	 * get a change's version ID
	 */
	public String getChangeVersionId(Change change){
		return change.versionId;
	}
	/*
	 * get a change's version date
	 */
	public String getChangeVersionDate(Change change){
		return change.versionDate.toString();
	}
	/*
	 * get a change's crawled date
	 */
	public String getChangeCrawledDate(Change change){
		return change.crawledDate.toString();
	}
	
}
