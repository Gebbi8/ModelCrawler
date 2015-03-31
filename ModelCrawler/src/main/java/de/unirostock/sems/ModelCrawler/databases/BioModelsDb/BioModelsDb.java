package de.unirostock.sems.ModelCrawler.databases.BioModelsDb;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.dump.UnsupportedCompressionAlgorithmException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import de.unirostock.sems.ModelCrawler.Properties;
import de.unirostock.sems.ModelCrawler.databases.BioModelsDb.exceptions.ExtractException;
import de.unirostock.sems.ModelCrawler.databases.BioModelsDb.exceptions.FtpConnectionException;
import de.unirostock.sems.ModelCrawler.databases.Interface.ChangeSet;
import de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase;
import de.unirostock.sems.ModelCrawler.helper.CrawledModelRecord;
import de.unirostock.sems.morre.client.MorreCrawlerInterface;
import de.unirostock.sems.morre.client.exception.MorreCommunicationException;
import de.unirostock.sems.morre.client.exception.MorreException;

// TODO: Auto-generated Javadoc
/**
 * The Class BioModelsDb.
 */
public class BioModelsDb implements ModelDatabase {

	/** The log. */
	private final Log log = LogFactory.getLog( BioModelsDb.class );

	/** The ftp url. */
	private URL ftpUrl;
	
	/** The ftp client. */
	private FTPClient ftpClient;
	
	/** The release list. */
	private List<BioModelRelease> releaseList = new ArrayList<BioModelRelease>();

	/** The temp dir. */
	protected File workingDir;
	
	/** The temp dir. */
	protected File tempDir;
	
	/** The config. */
	protected java.util.Properties config;

	/** The change set map. */
	protected Map<String, ChangeSet> changeSetMap = new HashMap<String, ChangeSet>();
	
	/** The morre client. */
	protected MorreCrawlerInterface morreClient = null;
//	protected GraphDatabase graphDb = null;

	/**
 * The Constructor.
 *
 * @param ftpUrl the ftp url
 * @param morreClient the morre client
 * @throws MalformedURLException the malformed url exception
 * @throws IllegalArgumentException the illegal argument exception
 */
public BioModelsDb(String ftpUrl, MorreCrawlerInterface morreClient) throws MalformedURLException, IllegalArgumentException {
		this.ftpUrl = new URL(ftpUrl);
//		this.graphDb = graphDb;
		this.morreClient = morreClient;

		if (!this.ftpUrl.getProtocol().toLowerCase().equals("ftp")) {
			// Protocoll is not ftp -> not (yet) supported
			log.error("Only ftp is support at the moment for BioModelsDataBase!");
			throw new IllegalArgumentException(
					"Only ftp ist support at the moment!");
		}

		log.info("Init new BioModels Database connector. URL: " + ftpUrl );
		// creating a ftp client
		ftpClient = new FTPClient();

		// prepares the working directory
		checkAndInitWorkingDir();

	}

	/**
	 * The Constructor.
	 *
	 * @param morreClient the morre client
	 * @throws MalformedURLException the malformed url exception
	 * @throws IllegalArgumentException the illegal argument exception
	 */
	public BioModelsDb( MorreCrawlerInterface morreClient ) throws MalformedURLException, IllegalArgumentException {
		this( Properties.getProperty("de.unirostock.sems.ModelCrawler.BioModelsDb.ftpUrl"), morreClient );
	}

	/* (non-Javadoc)
	 * @see de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase#listModels()
	 */
	@Override
	public List<String> listModels() {
		return new ArrayList<String>( changeSetMap.keySet() );
	}

	/* (non-Javadoc)
	 * @see de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase#listChanges()
	 */
	@Override
	public Map<String, ChangeSet> listChanges() {
		return changeSetMap;
	}
	
	/* (non-Javadoc)
	 * @see de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase#getModelChanges(java.lang.String)
	 */
	@Override
	public ChangeSet getModelChanges(String fileId) {
		return changeSetMap.get(fileId);
	}

	/* (non-Javadoc)
	 * @see de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase#cleanUp()
	 */
	@Override
	public void cleanUp() {
		
		// saving the properties
		saveProperties();
		
		// deletes the tempDir recursively
		try {
			FileUtils.deleteDirectory(tempDir);
		} catch (IOException e) {
			log.error("Error while cleaning up the temp dir!", e);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase#run()
	 */
	@Override
	public void run() {
		List<BioModelRelease> newReleases = new ArrayList<BioModelRelease>();

		log.info("Start cloning the BioModels DataBase by fetching the releases!");

		// Establish connection
		try {

			connect();
			retrieveReleaseList();

		} catch (IOException e) {
			log.fatal( "IOException while connecting and getting the releases!", e );
		} catch (FtpConnectionException e) {
			log.fatal( e );
		}

		if( config.getProperty("knownReleases", null) != null ) {
			// there are some releases we already indexed!
			List<String> knownReleases = Arrays.asList( config.getProperty("knownReleases", "").split(",") );

			// getting only the new releases
			Iterator<BioModelRelease> iter = releaseList.iterator();
			while( iter.hasNext() ) {
				BioModelRelease release = iter.next();
				if( knownReleases.contains( release.getReleaseName() ) == false ) {
					// the release is new and must be downloaded
					newReleases.add(release);
				}
			}

			if( log.isInfoEnabled() )
				log.info( MessageFormat.format( "{0} new release(s)", newReleases.size() ) );
		}
		else {
			// no releases were indexed before. Sadly now we have process them all
			// on the other hand, we could simply copy the list with all releases now :)
			newReleases.addAll(releaseList);
			if( log.isInfoEnabled() )
				log.info("every release is a new release...");
		}

		// sorting, just in case...
		Collections.sort(newReleases);
		
		// XXX Limiter
		int limiter = 1;
		
		// going throw the new release list an downloads every
		Iterator<BioModelRelease> iter = newReleases.iterator();
		while( iter.hasNext() ) {
			BioModelRelease release = iter.next();
			// do it (download, extract, compare to previous versions)
			processRelease( release );

			// if the download was succesfull, add the release to the known releases
			if( release.isDownloaded() && release.isExtracted() )
				config.setProperty( "knownReleases", config.getProperty("knownReleases", "") + "," + release.getReleaseName() );
			
//			if( limiter++ >= 5 )
//				break;
		}

		log.info("finished cloning BioModelsDatabase!");
	}

	/**
	 * Downloads, extracts and indexes the gives release
	 * must called for each new release CHRONOLOGICAL.
	 *
	 * @param release the release
	 */
	protected void processRelease( BioModelRelease release ) {
		
		// getting the current Date, for crawling TimeStamp
		Date crawledDate = new Date();
		
		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("start processing release {0}", release.getReleaseName()) );
		
		// try to download
		try {
			if( downloadRelease(release) == false ) {
				log.fatal( MessageFormat.format("Can not process release {0}", release.getReleaseName()) );
				return;
			}
		} catch (UnsupportedCompressionAlgorithmException e) {
			log.fatal("Can not download-extract the release! Unsupported CompressionAlgorithm" , e);
			return;
		}

		// try to extract
		try {
			extractRelease(release);
		}
		catch (IllegalArgumentException e) {
			log.fatal("Something went wrong with the release Object! (IllegalArgumentException) ", e);
			return;
		} catch (ExtractException e) {
			log.fatal("Error while extracting", e);
			return;
		}
		
		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("start transfering changes from release {0} into change sets", release.getReleaseName()) );
		
		// transfer the index from release
		Iterator<String> iter = release.getModelList().iterator();
		while( iter.hasNext() ) {
			tranferChange( iter.next(), release, crawledDate );
		}
	}

	/**
	 * Check and init working dir.
	 */
	protected void checkAndInitWorkingDir() {

		workingDir = new File( Properties.getWorkingDir(), Properties.getProperty("de.unirostock.sems.ModelCrawler.BioModelsDb.subWorkingDir") );
		tempDir = new File( workingDir, Properties.getProperty("de.unirostock.sems.ModelCrawler.BioModelsDb.subTempDir") );

		log.trace( "Preparing working dir " + workingDir.getAbsolutePath() );

		if( workingDir.exists() == false ) {
			// creates it!
			workingDir.mkdirs();
		}
		if( tempDir.exists() == false ) {
			// creates it!
			tempDir.mkdirs();
		}

		// inits the config
		config = new java.util.Properties();
		log.info("Loading working dir config");
		try {
			File configFile = new File( workingDir, Properties.getProperty("de.unirostock.sems.ModelCrawler.BioModelsDb.workingDirConfig", "config.properties") );
			if( configFile.exists() ) {
				FileReader configFileReader = new FileReader( configFile );
				if( configFileReader != null ) {
					config.load(configFileReader);
					configFileReader.close();
				}

			}

		}
		catch (IOException e) {
			log.fatal( "IOException while reading the workingdir config file", e );
		}

	}

	/**
	 * Save properties.
	 */
	protected void saveProperties() {

		if( config == null ) {
			config = new java.util.Properties();
		}

		try {
			FileWriter configFile = new FileWriter( new File( workingDir, Properties.getProperty("de.unirostock.sems.ModelCrawler.BioModelsDb.workingDirConfig", "config.properties") ));
			config.store(configFile, null);
			log.info("working dir config saved!");
		} catch (IOException e) {
			log.error( "Can not write the workingDir config file!", e );
		}

	}


	/**
	 * Connect.
	 *
	 * @throws FtpConnectionException the ftp connection exception
	 * @throws IOException the IO exception
	 * @throws SocketException the socket exception
	 */
	protected void connect() throws FtpConnectionException, IOException, SocketException {

		log.info("connecting to ftp server");

		try {

			// connect to FTP Server
			if( log.isTraceEnabled() )
				log.trace("establish socket connection");

			ftpClient.connect(ftpUrl.getHost(), ftpUrl.getPort() == -1 ? 21
					: ftpUrl.getPort());

			// login in
			if( log.isTraceEnabled() )
				log.trace("logging in");

			if( ftpClient.login( "anonymous", "anonymous" ) == false ) {
				throw new FtpConnectionException("Can not login with anonymous account!");
			}

			// switches to passiv mode
			if( log.isTraceEnabled() )
				log.trace("entering passiv mode");

			ftpClient.enterLocalPassiveMode();
			// set filetype to binary (we should only handle this type of files)
			// DO NOT REMOVE THIS LINE!! ;)
			ftpClient.setFileType( FTP.BINARY_FILE_TYPE );

			// change directory to the release directory
			if( log.isTraceEnabled() )
				log.trace("change directory to release directory");

			if( ftpClient.changeWorkingDirectory(ftpUrl.getPath()) == false ) {
				throw new FtpConnectionException("Can not change directory to release directory!");
			}

		} catch (SocketException e) {
			log.error("Can not connect to ftp server!", e);
			throw e;
		} catch (IOException e) {
			log.fatal("Can not connect to ftp server, IOException", e);
			throw e;
		}

	}

	/**
	 * Disconnect.
	 */
	protected void disconnect() {
		try {
			ftpClient.logout();
			ftpClient.disconnect();
		} catch (IOException e) {
			log.error("Error while disconnecting from ftp server, IOException", e);
		}
	}

	/**
	 * Retrieve release list.
	 *
	 * @return the list< bio model release>
	 * @throws IOException the IO exception
	 */
	protected List<BioModelRelease> retrieveReleaseList() throws IOException {

		// cleares the list
		releaseList.clear();

		if( log.isInfoEnabled() )
			log.info("retrieving release list form ftp server");

		// check if ftp client is connected
		if( ftpClient.isConnected() == false )
			throw new IOException( "Not connected to the server!" );

		// prepare the date parser
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

		// retrieve the dir list
		FTPFile[] dirs = ftpClient.listDirectories();
		for( int index = 0; index < dirs.length; index++ ) {
			// if the "file" is not a directory -> jump over
			if( !dirs[index].isDirectory() )
				continue;

			// Getting the TimeStamp of the Release
			Date releaseTimeStamp;
			try {
				// trying to parse it from the folder name, i.e. 2009-03-25
				releaseTimeStamp = dateFormat.parse( dirs[index].getName() );
			} catch (ParseException e) {
				// parser error -> pattern didn't match?
				// take the file timeStamp as fallback.
				releaseTimeStamp = dirs[index].getTimestamp().getTime();
			}

			// create a new release dataset
			BioModelRelease release = new BioModelRelease( dirs[index].getName(),
					ftpUrl.getPath() + dirs[index].getName(), releaseTimeStamp );

			// adding it to the list
			releaseList.add(release);
		}

		// sorting the list after release date ascending
		Collections.sort( releaseList );

		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("{0} releases on the server", releaseList.size() ) );

		return releaseList;
	}

	/**
	 * Download release.
	 *
	 * @param release the release
	 * @return true, if download release
	 * @throws UnsupportedCompressionAlgorithmException the unsupported compression algorithm exception
	 */
	private boolean downloadRelease( BioModelRelease release ) throws UnsupportedCompressionAlgorithmException {
		String archiv;
		File target;
		byte[] buffer = new byte[ 4096 ];

		if( release == null )
			return false;

		if( log.isInfoEnabled() )
			log.info( MessageFormat.format( "Start download release {0} from {1}", release.getReleaseName(), release.getFtpDirectory() ) );

		// if release already downloaded or extracted
		if( release.isDownloaded() || release.isExtracted() ) {
			log.warn( "The release is already download and/or extracted!" );
			return true;
		}

		try {
			// Changes the directory
			if( log.isDebugEnabled() )
				log.debug("changes to release directory");
			ftpClient.changeToParentDirectory();
			ftpClient.changeWorkingDirectory( release.getFtpDirectory() );

			// Finding the right file to download
			if( log.isDebugEnabled() )
				log.debug("trying to find the smbl only file");

			if( (archiv = findSbmlArchivFile()) == null ) {
				log.error("No matching file found!");
				return false;
			}
			
			if( log.isDebugEnabled() )
				log.debug( MessageFormat.format("found sbml file: {0}", archiv) );

			// Creating a TempFile and open OutputStream
			target = new File( tempDir, "BioModelsDb_" + release.getReleaseName() + ".tar" );
			//target = File.createTempFile( "BioModelsDb_" + release.getReleaseName() + "_", ".tar" );
			BufferedOutputStream targetStream = new BufferedOutputStream( new FileOutputStream(target) );

			if( log.isDebugEnabled() )
				log.debug( MessageFormat.format("download and uncompress {0} to {1}", archiv, target.getAbsolutePath() ));

			// download it...
			InputStream downStream = ftpClient.retrieveFileStream(archiv);

			// do the uncompress
			InputStream uncompressedStream;
			if( archiv.endsWith(".gz") ) {
				// use gzip uncompression
				uncompressedStream = new GzipCompressorInputStream(downStream);
				if( log.isTraceEnabled() )
					log.trace("using gzip");
			}
			else if( archiv.endsWith(".bzip") || archiv.endsWith(".bz") || archiv.endsWith(".bzip2") || archiv.endsWith(".bz2") ) {
				// use bzip2 uncompression
				uncompressedStream = new BZip2CompressorInputStream(downStream);
				if( log.isTraceEnabled() )
					log.trace("using bzip");
			}
			else if( archiv.endsWith(".tar") ) {
				// uncompressed tar-ball
				uncompressedStream = downStream;
				if( log.isTraceEnabled() )
					log.trace("no compression, just a simple tar ball");
			}
			else {
				targetStream.close();
				throw new UnsupportedCompressionAlgorithmException( "Unknown file extension!" );
			}

			// do it...
			int total = 0, red = 0;
			while( (red = uncompressedStream.read(buffer)) != -1 ) {
				targetStream.write(buffer, 0, red);
				total = total + red;
//				System.out.println( MessageFormat.format("{0} ({1})", total, red) );
			}

			// close the output Stream
			targetStream.flush();
			targetStream.close();

			// close the input Stream
			downStream.close();

			if( log.isInfoEnabled() )
				log.info( MessageFormat.format("download complete, {0} bytes", total) );

			if( ftpClient.completePendingCommand() == false ) {
				// file transfer was not successful!
				//				target.delete();
				return false;
			}

			// successful!
			// setting position of tar-ball in Release DataHolder
			release.setArchivFile(target);			

		}
		catch (UnsupportedCompressionAlgorithmException e) {
			log.error("Can not uncompress the release! Unsupported Compression Algo!", e);
			return false;
		}
		catch (IOException e) {
			log.error("IOException while downloading and extracting the release!", e);
			return false;
		}

		return true;
	}

	/**
	 * Find sbml archiv file.
	 *
	 * @return the string
	 * @throws IOException the IO exception
	 */
	private String findSbmlArchivFile() throws IOException {

		FTPFile[] files = ftpClient.listFiles();
		for( int index = 0; index < files.length; index++ ) {

			// matching the filename
			// ^BioModels_Database-r{[0-9]*}_pub-sbml_files\\.tar \\.bz2$
			// simple it ends with sbml file declaration and an archiv file extension
			//if( files[index].getName().endsWith("sbml_files.tar.bz2") == true ) {
			if( files[index].getName().contains("sbml_file") == true ) {
				//System.out.println( files[index].getSize() );
				return files[index].getName();
			}
		}

		return null;
	}

	/**
	 * Extract release.
	 *
	 * @param release the release
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws ExtractException the extract exception
	 */
	private void extractRelease( BioModelRelease release ) throws IllegalArgumentException, ExtractException {

		// already extracted or not even downloaded - just for safety... 
		if( !release.isDownloaded() || release.isExtracted() )
			throw new IllegalArgumentException("The release is suposed to be downloaded and not extracted!");

		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("Start extracting release {0}", release.getReleaseName()) );

		// Map for Biomodel-Files
		// Key = fileId, Value = Path in ContentDirectory
		Map<String, File> fileMap = new HashMap<String, File>();

		// extract dir
		File contentDir = new File(tempDir, release.getReleaseName() );
		contentDir.mkdirs();	// creates content dir in temp dir

		try {
			if( log.isDebugEnabled() )
				log.debug("Opening archive");

			// opens Archivfile for reading
			InputStream tarFileStream = new FileInputStream( release.getArchivFile() );
			// creates InputStream for uncompressing - Format is set to tar
			// ArchiveInputStream archivStream = (ArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream(tarFileStream);
			ArchiveInputStream archivStream = new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, tarFileStream);

			ArchiveEntry entry = null;

			if( log.isDebugEnabled() )
				log.debug("extract entries!");

			// looping throw entries
			while ( (entry = archivStream.getNextEntry()) != null ) {
				File entryFile = new File( contentDir, entry.getName() );

				if( entry.isDirectory() ) {
					// directory

					if( log.isTraceEnabled() )
						log.trace( MessageFormat.format("Extract directory {0}", entryFile.getAbsolutePath() ));

					if( !entryFile.exists() ) {
						// directroy does not exists
						if( !entryFile.mkdirs() )
							throw new IllegalStateException("Can not create directory " + entryFile.getAbsolutePath() );
					}
				}
				else {
					// file

					if( log.isTraceEnabled() )
						log.trace( MessageFormat.format("Extract file {0}", entryFile.getAbsolutePath() ));

					OutputStream entryStream = new FileOutputStream(entryFile);
					IOUtils.copy(archivStream, entryStream);
					entryStream.close();

					// add file to fileMap 
					// in BioModelsDB the filename is the fileId + .xml
					String fileName = entryFile.getName();
					int extensionPos = fileName.lastIndexOf('.');
					if( fileName.substring(extensionPos+1).toLowerCase().equals("xml") ) {
						// filename as xml ending
						String fileId = fileName.substring(0, extensionPos);

						if( log.isDebugEnabled() )
							log.debug( MessageFormat.format("Found model {0} from file {1}", fileId, fileName) );

						// put it in the map
						fileMap.put(fileId, entryFile);
					}
				}

				// looping
			}

			// set the result into the release
			release.setContentDir(contentDir, fileMap);
		}
		catch (IllegalStateException e) {
			log.error( e.getMessage() );
			throw new ExtractException(e);
		} catch (ArchiveException e) {
			String message = MessageFormat.format("ArchiveException while extracting release {0}", release.getReleaseName());
			log.error( message );
			throw new ExtractException(message, e);
		} catch (IOException e) {
			String message = MessageFormat.format("IOException while extracting release {0}", release.getReleaseName());
			log.error( message );
			throw new ExtractException(message, e);
		} 
	}

	/**
	 * Tranfer change.
	 *
	 * @param fileId the file id
	 * @param release the release
	 * @param crawledDate the crawled date
	 */
	private void tranferChange( String fileId, BioModelRelease release, Date crawledDate ) {

		boolean isChangeNew = false;
		
		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("Check if model {0} from release {1} is a new change", fileId, release.getReleaseName()) );
		
		BioModelsChangeSet changeSet = null;
		if( changeSetMap.containsKey(fileId) ) {
			// if fileId is already known -> get it from changeSetMap
			changeSet = (BioModelsChangeSet) changeSetMap.get(fileId);
			if( log.isDebugEnabled() )
				log.debug("ChangeSet exists, fileId is not unknown!");
		}

		// create the Change-Entry
		BioModelsChange change = new BioModelsChange(fileId, release.getReleaseName(), release.getReleaseDate(), crawledDate);
		
		// sets soure meta information
		change.setMeta(CrawledModelRecord.META_SOURCE, CrawledModelRecord.SOURCE_BIOMODELS_DB);
		change.setModelType( CrawledModelRecord.TYPE_SBML );
		
		// set up the xml file and calc the hash
		change.setXmlFile( release.getModelPath(fileId) );
		if( log.isTraceEnabled() )
			log.trace( MessageFormat.format("calced file hash: {0}", change.getHash()) );
		
		// --------
		// try to find some parents for this model.
		// is a changeSet for this model available?
		if( changeSet != null ) {
			// yes -> compare hashes from current and latest
			
			if( log.isTraceEnabled() )
				log.trace("compare hash with latest from changeSet");
			
			BioModelsChange latest = ((BioModelsChange) changeSet.getLatestChange());
			// null check
			if( latest != null ) {
				// compare hashes and checks if the "latest" version is older than the processing change
				if( change.getHash().equals(latest.getHash()) == false && latest.getVersionDate().compareTo( change.getVersionDate() ) < 0 ) {
					isChangeNew = true;
					// add the parent
					change.addParent( latest.getFileId(), latest.getVersionId() );
					if( log.isInfoEnabled() )
						log.info("hashs are not equal -> new version");
				}
			}
		}
		else {
			// no changeSet available...
			
			if( log.isDebugEnabled() )
				log.debug("ChangeSet does not exists, checking database");
			
			// ... creates one ...
			changeSet = new BioModelsChangeSet(fileId);
			// only put this in the map, if there is a latest version anywhere

			// if GraphDb is available for this instance
			if( morreClient != null  ) {

				// TODO cache the result of the latest request!
				
				if( log.isTraceEnabled() )
					log.trace("start checking database");
				
				// try to get the latest version of this model
				CrawledModelRecord latest = null;
				try {
					latest = CrawledModelRecord.extendDataholder( morreClient.getLatestModelVersion(fileId) );
				} catch (MorreCommunicationException e) {
					log.fatal("Getting latest model version, to check, if processed model version is new, failed", e);
				} catch (MorreException e) {
					// error occures, when fileId is unknown to the database -> so we can assume the change is new!
					log.warn("GraphDatabaseError while checking, if processed model version is new. It will be assumed, that this is unknown to the database!", e);
					// set no parent
					isChangeNew = true;
				}
				
				if( latest == null || !latest.isAvailable() ) {
					// there is no latest version, neither in the database than in the local changeset
					isChangeNew = true;
				}
				else {
					// latest model available
					
					if( log.isTraceEnabled() )
						log.trace("successfully received latest from database");
					
					String latestHash = latest.getMeta(BioModelsChange.META_HASH);
					if( latestHash == null ) {
						// TODO
						log.error("There is no hash in the latest model. Maybe the database is inconsistent.");
						isChangeNew = true;
					}
					// if hash exists
					// compare hashes and checks if the "latest" version is older than the processing change
					else if( latestHash.equals( change.getHash() ) == false && latest.getVersionDate().compareTo( change.getVersionDate() ) < 0 ) {
						isChangeNew = true;
						// set parent
						change.addParent( latest.getFileId(), latest.getVersionId() );
						
						if( log.isInfoEnabled() )
							log.info("hashs are not equal -> new version");
					}
				}
			}

		}

		if( isChangeNew )  {
			// pushs it into changeSet
			changeSet.addChange(change);
			if( !changeSetMap.containsKey(fileId) ) {
				// make this changeset public!
				changeSetMap.put(fileId, changeSet);
			}
			
			if( log.isDebugEnabled() )
				log.debug("put new version into change set");
		} else if( log.isDebugEnabled() ) {
			log.debug("not a new version of model");
		}
			

	}

}
