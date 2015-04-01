package de.unirostock.sems.ModelCrawler.databases.PMR2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.io.Util;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.binfalse.bfutils.GeneralTools;
import de.unirostock.sems.ModelCrawler.Config;
import de.unirostock.sems.ModelCrawler.databases.Interface.Change;
import de.unirostock.sems.ModelCrawler.databases.Interface.ChangeSet;
import de.unirostock.sems.ModelCrawler.databases.Interface.ModelDatabase;
import de.unirostock.sems.ModelCrawler.databases.PMR2.exceptions.HttpException;
import de.unirostock.sems.ModelCrawler.helper.CrawledModelRecord;
import de.unirostock.sems.bives.Main.ExecutionException;
import de.unirostock.sems.bives.tools.DocumentClassifier;
import de.unirostock.sems.morre.client.exception.MorreCommunicationException;
import de.unirostock.sems.morre.client.exception.MorreException;

public class PmrDb extends ModelDatabase {

	private static final long serialVersionUID = -5332599172641988743L;
	@JsonIgnore
	private final Log log = LogFactory.getLog( PmrDb.class );
	
	protected String hashAlgo = "MD5";
	protected URL repoListUrl = null;
	
	@JsonIgnore
	protected DocumentClassifier classifier = null;
	@JsonIgnore
	protected Set<String> fileExtensionBlacklist = new HashSet<String>();
	@JsonIgnore
	protected Map<String, ChangeSet> changeSetMap = new HashMap<String, ChangeSet>();
	@JsonIgnore
	protected WorkingDirConfig config = null;
	
	private class WorkingDirConfig {
		
		private Map<String, String> repositories = new HashMap<String, String>();

		public Map<String, String> getRepositories() {
			return repositories;
		}

		public void setRepositories(Map<String, String> repositories) {
			this.repositories = repositories;
		}
		
	}
	
	// REMIND there is difference between ChangeSet and Changeset
	// ChangeSet is a ModelCrawler Dataholder class
	// and Changeset a JavaHg Dataholder class
	
	/**
	 * Default dataholder
	 * 
	 */
	public PmrDb() {}
	
	@Override
	public List<String> listModels() {
		return new ArrayList<String>( changeSetMap.keySet() );
	}

	@Override
	public Map<String, ChangeSet> listChanges() {
		return changeSetMap;
	}

	@Override
	public ChangeSet getModelChanges(String fileId) {
		return changeSetMap.get(fileId);
	}
	
	public String getHashAlgo() {
		return hashAlgo;
	}

	public void setHashAlgo(String hashAlgo) {
		this.hashAlgo = hashAlgo;
	}

	public URL getRepoListUrl() {
		return repoListUrl;
	}

	public void setRepoListUrl(URL repoListUri) {
		this.repoListUrl = repoListUri;
	}

	@Override
	public void close() {
		// save the config
		saveProperties();

		// deletes the tempDir recursively
		try {
			FileUtils.deleteDirectory(tempDir);
		} catch (IOException e) {
			log.error("Error while cleaning up the temp dir!", e);
		}
	}

	@Override
	public Map<String, ChangeSet> call() {
		List<String> repositories = null;
		
		if( morreClient == null ) {
			log.error("No Morre crawler interface provided!");
			throw new IllegalArgumentException("No Morre crawler interface provided!");
		}
		
		// Http only!
		if( !repoListUrl.getProtocol().toLowerCase().startsWith("http") )
			throw new IllegalArgumentException("Only http is supported for the Repository List at the moment!");

		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("Init new PMR2 Connector based on Repolist: {0}", this.repoListUrl) );
		
		// init Document classifier
		classifier = new DocumentClassifier ();

		if( log.isInfoEnabled() )
			log.info("Started BiVeS Classifier");

		// Prepare WorkingDir 
		init();
		log.info("Start crawling the PMR2 Database by going throw the Mercurial Workspaces");
		
		// list all available Repos
		try {
			repositories = getRepositoryList();
		} catch (HttpException e) {
			log.fatal("Can not download RepositoryList", e);
		}
		
		// limiting releases
		if( limit > 0 ) {
			if( log.isInfoEnabled() )
				log.info( MessageFormat.format("Limit processed Repositories to {0}", limit) );
			
			repositories = repositories.subList(0, limit);
		}
		
		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("Iterate over {0} repositories", repositories.size()) );

		// process all repos
		for( String repoLink : repositories ) {
			processRepository(repoLink);
		}

		log.info("Finished crawling PMR2 Database.");
		return changeSetMap;
	}

	protected void init() {
		
		log.trace( "Preparing working dir " + workingDir.getAbsolutePath() );
		
		if( workingDir.exists() == false ) {
			// creates it!
			workingDir.mkdirs();
		}
		// create temp dir
		createTempDir();
		
		try {
			// inits the config
			log.info("Loading working dir config");
			File configFile = new File( workingDir, Config.getConfig().getWorkingDirConfig() );
			config = Config.getObjectMapper().readValue( configFile, WorkingDirConfig.class );
		}
		catch (IOException e) {
			log.fatal( "IOException while reading the workingdir config file", e );
		}
		
		// fill own copy of blacklist
		fileExtensionBlacklist.addAll(
				Arrays.asList( Config.getConfig().getExtensionBlacklist() )
			);
	}

	/**
	 * Returns a non existent temporary file
	 * @return
	 */
	protected File getTempFile() {
		File temp = new File( tempDir, UUID.randomUUID().toString() );
		while( temp.exists() ) {
			temp = new File( tempDir, UUID.randomUUID().toString() );
		} 

		return temp;
	}

	protected void saveProperties() {

		if( config == null ) {
			config = new WorkingDirConfig();
		}

		try {
			// save the config
			log.info("Saving working dir config");
			
			File configFile = new File( workingDir, Config.getConfig().getWorkingDirConfig() );
			Config.getObjectMapper().writeValue(configFile, config);
			
			log.info("working dir config saved!");
		} catch (IOException e) {
			log.error( "Can not write the workingDir config file!", e );
		}

	}


	/**
	 * Retrieves the txt Repository List and puts it in a list
	 * 
	 * @return
	 * @throws HttpException
	 */
	protected List<String> getRepositoryList() throws HttpException {
		List<String> repoList = new LinkedList<String>();
		
		try {
			InputStream input = repoListUrl.openStream();
			
			Scanner scanner = new Scanner(input);
			scanner.useDelimiter("\n");
			
			while( scanner.hasNext() ) {
				String repo = scanner.next();
				if( repo != null && !repo.isEmpty() )
					repoList.add( repo );				
			}
			
			scanner.close();

		} catch (IOException e) {
			throw new HttpException("IOException while downloading RepositoryList", e);
		}

		return repoList;
	}


	/**
	 * Creates the directory for the given Repository
	 *  
	 * @param repository
	 * @return
	 */
	protected File makeRepositoryDirectory( String repository ) {

		File directory = null;
		String name = null;

		String repoHash = calculateRepositoryHash(repository);

		// trys to get the RepoDir from config file
		if( (directory = getRepositoryDirectory(repository)) == null ) {
			// when fails -> generates a name

			//		name = repository.replace("http://", "");
			//		name = repository.replace("/", "_");
			name = repository.substring( repository.lastIndexOf('/') ) + "_" + repository.hashCode();

			// check if directory already exists
			directory = new File( workingDir, name );
			if( directory.exists() && directory.isDirectory() ) {
				// exists -> extends with a number
				String newName = name;
				int i = 2;
				do {
					newName = name + "_" + i;
					directory = new File( workingDir, newName );
					i++;
				} while( directory.exists() );
				name = newName;
			}

			// store the directory name into the config
			config.getRepositories().put(repoHash, name);

		}

		// when directory does not exists...
		if( !directory.exists() ) {
			// ...creates it!
			directory.mkdirs();
		}

		return directory;

	}

	/**
	 * Gets the Path to the Repository Directory out of Workspace config or null if it fails
	 * 
	 * @param repository
	 * @return File
	 */
	protected File getRepositoryDirectory( String repository ) {
		String repoHash = calculateRepositoryHash(repository);
		String name = null;

		if( (name = config.getRepositories().get(repoHash)) != null )
			return new File( workingDir, name );
		else
			return null;
	}

	/**
	 * Calculates the hash from the Repository URL
	 * 
	 * @param repository
	 * @return
	 */
	private String calculateRepositoryHash( String repository ) {
		String repoHash = null;

		try {
			MessageDigest digest = MessageDigest.getInstance(hashAlgo);
			digest.update( repository.getBytes() );
			repoHash = (new BigInteger( digest.digest() )).toString(16);
		} catch (NoSuchAlgorithmException e) {
			log.fatal( MessageFormat.format("Can not calc Repository Hash for {0}!", repository), e );
		}

		return repoHash;
	}
	
	protected void processRepository( String repoLink ) {
		Git repo = null;
		boolean hasChanges = false;
		
		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("Check Repository {0}", repoLink ) );

		File location = getRepositoryDirectory(repoLink);
		if( location == null ) {
			// clone repo
			
			if( log.isDebugEnabled() )
				log.debug( MessageFormat.format("Repository {0} is unknown. Create new folder and clone it", repoLink) );

			// Repo is unknown -> make a directory
			try {
				location = makeRepositoryDirectory(repoLink);
				repo = Git.cloneRepository()
						.setURI(repoLink)
						.setDirectory(location)
						.setCloneSubmodules(true)
						.setCloneAllBranches(false)
						.call();
				
				if( log.isInfoEnabled() )
					log.info( MessageFormat.format("Repository {0} has been cloned into {1}", repoLink, location.getAbsolutePath()) );

				// of course there are changes
				hasChanges = true;
				
			}
			catch (GitAPIException e) {
				log.error( MessageFormat.format("Can not clone Git Repository {0} into {1}", repoLink, location.getAbsolutePath()), e );
				return;
			}
				
		}
		else {

			if( log.isDebugEnabled() )
				log.debug( MessageFormat.format("Repository {0} is known. Perform a Pull-Request into local copy {1}", repoLink, location.getAbsolutePath()) );

			// Repo is already known -> make a pull
			try {
				repo = Git.open(location);
				PullResult pullResult = repo.pull().call();
				
				if( pullResult.isSuccessful() == false ) {
					log.warn( MessageFormat.format("Pull request failed! from {0} into {1} ", repoLink, location.getAbsolutePath()) );
					hasChanges = false;
				}
				else if( pullResult.getFetchResult().getTrackingRefUpdates().size() > 0 )
					hasChanges = true;
				else
					hasChanges = false;
			}
			catch (GitAPIException | IOException e) {
				log.error( MessageFormat.format("Can not pull Git Repository {0} into {1}", repoLink, location.getAbsolutePath()), e );
				return;
			}

			if( log.isInfoEnabled() ) {
				if( hasChanges )
					log.info( MessageFormat.format("Pulled changes from {0} into local copy {1}", repoLink, location.getAbsolutePath()) );
				else
					log.info( "No changes to pull. Local copy is up to date." );
			}
		}

		if( hasChanges ) {
			// Scan for cellml and other model files and transfer them
			scanAndTransferRepository(repoLink, location, repo);
		}
		
		// closes the repo
		if( repo != null )
			repo.close();
	}
	
	protected void scanAndTransferRepository( String repoUrl, File location, Git repo ) {
		// select all relevant files
		// than going throw the versions
		List<RelevantFile> relevantFiles;
		Iterable<RevCommit> relevantVersions;

		// TODO Logging!

		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("Start scanning {0} for changes", repoUrl) );

		// select all relevant files
		relevantFiles = scanRepository(location);

		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("Found {0} relevant files.", relevantFiles.size()) );

		// generating the fileId and looking for the latestVersion
		try {
			for( RelevantFile file : relevantFiles ) {
				file.generateFileId(repoUrl);
				if( log.isDebugEnabled() )
					log.debug( MessageFormat.format("Generated fileId {0} for file {1}", file.getFileId(), file.getFilePath()) );

				searchLatestKnownVersion( file );
			}
		}
		catch (UnsupportedEncodingException e) {
			log.fatal("Unsupported Encoding. Can not generate fileId", e);
		}

		// detect all relevant versions
		relevantVersions = detectRelevantVersions(repo, relevantFiles);

		if( relevantVersions == null )
			// no version is relevant - exit
			return;

		// sorting them (just in case...)
		// TODO is sorting necessary?
//		Collections.sort(relevantVersions, new Comparator<RevCommit>() {
//			@Override
//			public int compare(RevCommit arg0, RevCommit arg1) {
//				return new Date( arg0.getCommitTime() ).compareTo( new Date( arg1.getCommitTime() ));
//			}
//		} );

		// make it!
		// (going throw each relevant Version and saves all relevant Files in every relevant - and new - Version)
		try {
			iterateRelevantVersions(repo, location, relevantFiles, relevantVersions);
		} catch (IOException e) {
			log.fatal( MessageFormat.format("IOException while iteration throw relevant Versions in {0}",  location), e );
		}

		for( RelevantFile file : relevantFiles ) {
			if( file.getChangeSet() != null ) {
				// when the RelevantFile class contains a ChangeSet Object
				// than there are some changes to store, so we can it to the changeSetMap
				changeSetMap.put( file.getFileId(), file.getChangeSet() );
			}
		}

	}

	protected List<RelevantFile> scanRepository( File location ) {
		List<RelevantFile> relevantFiles = new LinkedList<RelevantFile>();

		// scans the directory recursively
		scanRepositoryDir( location, location, relevantFiles );

		return relevantFiles;
	}

	private void scanRepositoryDir( File base, File dir, List<RelevantFile> relevantFiles ) {

		if( log.isTraceEnabled() )
			log.trace( MessageFormat.format("Scanning {0}", base) );

		String[] entries = dir.list();
		// nothing to scan in this dir
		if( entries == null )
			return;

		// looping throw all directory elements
		for( int index = 0; index < entries.length; index++ ) {
			File entry = new File( dir, entries[index] );

			if( entry.isDirectory() && entry.exists() && !entry.getName().startsWith(".") ) {
				// Entry is a directory and not hidden (begins with a dot) -> recursive
				scanRepositoryDir(base, entry, relevantFiles);
			}
			else if( entry.isFile() && entry.exists() ) {
				// Entry is a file -> check if it is relevant
				
				if( log.isTraceEnabled() )
					log.trace( MessageFormat.format("Found {0}. Check relevance...", entry) );
				
				if( fileExtensionBlacklist.contains( FilenameUtils.getExtension(entry.getName()) ) ) {
					// file extension is blacklisted
					if( log.isTraceEnabled() )
						log.trace("file extension is blacklisted. Skip this file...");
					continue;
				}
				
				RelevantFile file;
				if( (file = isRelevant(base, entry)) != null ) {
					// adds it
					relevantFiles.add(file);
					if( log.isTraceEnabled() )
						log.trace("Is relevant. Adds it.");
				} else if( log.isTraceEnabled() )
					log.trace("Is not relevant.");

			}

		}

	}

	/**
	 * Checks if the file is a model aka relevant <br>
	 * Returns a RelevantFile object if it is or null
	 * 
	 * @param base
	 * @param model
	 * @return
	 */
	private RelevantFile isRelevant( File base, File model ) {
		int type = 0;
		RelevantFile relevantFile = null;
		// classify the file and check if it is relevant
		type = classifier.classify(model);

		if( (type & DocumentClassifier.XML) > 0 && ((type & DocumentClassifier.SBML) > 0 || (type & DocumentClassifier.CELLML) > 0) ) {
			// File is an xml document and consists of sbml or cellml model data
			// create a relevant file object

			// make path relative to Repo base dir
			Path basePath = Paths.get( base.toString() );
			Path modelPath = Paths.get( model.toString() );
			Path relativPath = basePath.relativize(modelPath);

			// creating relevantFile object
			relevantFile = new RelevantFile( relativPath.toString() );
			relevantFile.setType(type);

			//			try {
			//				relevantFile = new RelevantFile( RelativPath.getRelativeFile(model, base).toString() );
			//				relevantFile.setType(type);
			//			} catch (IOException e) {
			//				log.error( MessageFormat.format("IOException while generating relativ path to file {0} in repository {1}", model, base), e);
			//			}

		}

		return relevantFile;
	}

	protected void searchLatestKnownVersion( RelevantFile relevantFile ) {
		String versionId = null;
		Date versionDate = null;
		ChangeSet changeSet = null;

		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("Searches latest known version for model {0}", relevantFile.getFileId()) );

		if( (changeSet = changeSetMap.get(relevantFile.getFileId())) != null ) {
			// there is a changeSet for this fileId, get the latestChange

			if( log.isDebugEnabled() )
				log.debug("ChangeSet available");

			Change latestChange = changeSet.getLatestChange();
			if( latestChange != null ) {
				versionId = latestChange.getVersionId();
				versionDate = latestChange.getVersionDate();
			}
			else if( log.isDebugEnabled() ) {
				log.debug("But no change setted");
			}
		}

		// versionId and versionDate are still not set
		if( versionId == null && versionDate == null ) {

			if( log.isDebugEnabled() )
				log.debug("Start database request");

			// search in database
			CrawledModelRecord latest = null;
			try {
				latest = CrawledModelRecord.extendDataholder( morreClient.getLatestModelVersion( relevantFile.getFileId() ) );
			} catch (MorreCommunicationException e) {
				log.fatal( MessageFormat.format("Getting latest model version from {0}, to check, if processed model version is new, failed", relevantFile.getFileId()), e);
			} catch (MorreException e) {
				// error occurs, when fileId is unknown to the database -> so we can assume the change is new!
				log.warn("GraphDatabaseError while checking, if processed model version is new. It will be assumed, that this is unknown to the database!", e);
			}

			if( latest != null && latest.isAvailable() ) {
				versionId = latest.getVersionId();
				versionDate = latest.getVersionDate();
			}
		}

		if( log.isInfoEnabled() ) {
			if( versionId != null && versionDate != null )
				log.info( MessageFormat.format("Found latest version for {0} : {1}@{2}", relevantFile.getFileId(), versionId, versionDate) );
			else
				log.info( MessageFormat.format("Found no latest version for {0}. Must be the first occure", relevantFile.getFileId()) );
		}

		relevantFile.setLatestKnownVersion(versionId, versionDate, (PmrChangeSet) changeSet);

	}

	protected Iterable<RevCommit> detectRelevantVersions( Git repo, List<RelevantFile> relevantFiles ) {
		Date oldestLatestVersionDate = null;
		boolean foundOldestLatestVersionDate = false;
		Iterable<RevCommit> relevantVersions = null;

		if( log.isDebugEnabled() )
			log.debug("start detection of relevant hg versions");

		if( relevantFiles.size() == 0 ) {
			if( log.isInfoEnabled() )
				log.info( "List of relevantFiles is empty. So no version is relevant." );

			return null;
		}

		// Build up log command
		LogCommand logCmd = repo.log();

		// put the list into the array and gets the oldestLatestVersion :)
		for( RelevantFile file : relevantFiles ) {
			logCmd.addPath( file.getFilePath() );

			// checks if the current processed relevantFile has an older latestVersion as the
			// former olderLatestVersion or some file hasn't a parent, so we can not delete any version from the list
			if( oldestLatestVersionDate == null ) {
				if( foundOldestLatestVersionDate == false ) {
					oldestLatestVersionDate = file.getLatestVersionDate();
					foundOldestLatestVersionDate = true;
				}
			}
			else if( file.getLatestVersionDate() != null && oldestLatestVersionDate.compareTo( file.getLatestVersionDate() ) > 0 ) {
				oldestLatestVersionDate = file.getLatestVersionDate();
			}

		}

		if( log.isDebugEnabled() )
			log.debug( MessageFormat.format("execute Log command for {0} file(s)", relevantFiles.size()) );

		// perform the log command to evaluate all interesting commits
		try {
			relevantVersions = logCmd.call();
		} catch (GitAPIException e) {
			log.error("Error while executing log command", e);
			return null;
		}
		int numVersions = GeneralTools.sizeOfIterable( relevantVersions );

		if( oldestLatestVersionDate == null ) {
			// oldestLatestVersionDate is null -> there is no latest version known for any of the relevantFiles/-Models
			if( log.isInfoEnabled() )
				log.info( MessageFormat.format("Found {0} Commits. Can not skip any of them, because no one is indexed", numVersions) );
			
			Iterator<RevCommit> changesetIter = relevantVersions.iterator();
			while( changesetIter.hasNext() ) {
				
			}
		}
		else {
			if( log.isInfoEnabled() )
				log.info( MessageFormat.format("Found {0} Commits, removes all Commits older as {1} (oldestLatestVersion) from the list", numVersions, oldestLatestVersionDate) );

			// remove every Changeset which is older as the oldestLatestVersion (because they are really uninteresting)
			Iterator<RevCommit> changesetIter = relevantVersions.iterator();
			while( changesetIter.hasNext() ) {
				if( new Date( changesetIter.next().getCommitTime() ).compareTo(oldestLatestVersionDate) < 0 )
					changesetIter.remove();
			}

			if( log.isInfoEnabled() )
				log.info( MessageFormat.format("{0} Commits left for examination", GeneralTools.sizeOfIterable(relevantVersions)) );

		}
		
		return relevantVersions;
	}

	protected void iterateRelevantVersions( Git repo, File location, List<RelevantFile> relevantFiles, Iterable<RevCommit> relevantVersions ) throws IOException {
		Date crawledDate = new Date();

		if( log.isInfoEnabled() )
			log.info( MessageFormat.format("Going throw all relevant versions of {0}", location) );

		for( RevCommit currentCommit : relevantVersions ) {
			String currentName = currentCommit.getName();
			Date currentVersionDate = new Date( currentCommit.getCommitTime() );

			if( log.isInfoEnabled() )
				log.info( MessageFormat.format("Update to {0} Message: {1}", currentName, currentCommit.getShortMessage()) );

			// update to currentChangeset
			
			
			try {
				repo.checkout()
				.setStartPoint(currentCommit)
				.call();
			} catch (IOException e) {
				log.error( MessageFormat.format("IOException while updating {0} to {1}. skip this repo after now.", location, currentName), e);
				return;
			} catch (ExecutionException e) {
				log.error( MessageFormat.format("IOException while updating {0} to {1}. skip this repo after now.", location, currentName), e);
				return;
			}

			// get all added or modified files in this Changeset
			List<String> changedFiles = new ArrayList<String>();
			changedFiles.addAll( currentCommit.getAddedFiles() );
			changedFiles.addAll( currentCommit.getModifiedFiles() );

			if( log.isInfoEnabled() )
				log.info( MessageFormat.format("{0} changed files in this version", changedFiles.size()) );

			// going throw the relevant files
			for( RelevantFile file : relevantFiles ) {
				boolean hasChanges = false;

				if( log.isInfoEnabled() )
					log.info( MessageFormat.format("Check model {0}", file.getFileId()) );
				
				File fileLocation = new File( location, file.getFilePath() );
				if( !fileLocation.exists() ) {
					// file does not exists -> skip
					if( log.isInfoEnabled() )
						log.info("Model does not exists in this version -> skip it.");
					continue;
				}
				
				// there is already a parent version
				if( file.getLatestVersionId() != null && file.getLatestVersionDate() != null ) {
					if( file.getLatestVersionId().equals(currentName) || file.getLatestVersionDate().compareTo(currentVersionDate) >= 0 ) {
						// if latest version of this file is newer or equal with the current processed Version
						// skip this file
						if( log.isInfoEnabled() )
							log.info("Current version is to old -> no changes.");

						continue;
					}
				}
				else {
					// there is no parent Version -> so there are changes
					hasChanges = true;
					if( log.isDebugEnabled() )
						log.debug("Model has no parents -> this is a new version.");
				}

				// if there are no change detected so far, so have to go deeper
				if( hasChanges == false ) {

					if( log.isTraceEnabled() )
						log.trace("Check if model is in the changed files list");

					// file is in the list of changedFiles
					if( changedFiles.contains(file.getFilePath()) == true ) {
						hasChanges = true;
						if( log.isDebugEnabled() )
							log.debug("Model is in the changed files list.");
					}
				}

				if( hasChanges ) {
					// this file has change or is new -> archive it!
					if( log.isInfoEnabled() )
						log.info("Model has changes. Adds it to its ChangeSet");

					PmrChange change = new PmrChange(file.getFileId(), file.getRepositoryUrl(), file.getFilePath(), currentName, currentVersionDate, crawledDate);
					// set some Meta information
					change.setMeta( CrawledModelRecord.META_SOURCE, CrawledModelRecord.SOURCE_PMR2 );
					if( (file.getType() & DocumentClassifier.SBML) > 0 )
						change.setModelType( CrawledModelRecord.TYPE_SBML );
					else if( (file.getType() & DocumentClassifier.CELLML) > 0 )
						change.setModelType( CrawledModelRecord.TYPE_CELLML );

					// copy the file to a templocation
					File tempFile = getTempFile();
					FileUtils.copyFile( fileLocation, tempFile);
					change.setXmlFile(tempFile);

					// add the change to the ChangeSet (ChangeSet is administrated by RelevantFile
					file.addChange(change);
				}
				else if( log.isInfoEnabled() )
					log.info("Model has no changes.");

			}

		}

	}

}
