package de.unirostock.sems.ModelCrawler.databases.Interface;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;


/**
 * 
 * set of changes for a specific model
 * 
 * @author tgeb
 *
 */
public abstract class ChangeSet {
	
	@JsonIgnore
	private final Log log = LogFactory.getLog( ChangeSet.class );
	
	/**
	 * identifier from repo url and relative file path in repo
	 */
	protected String fileId;
	protected NavigableSet<Change> changes;
	
	public Set<Change> getChanges() {
		return changes;
	}
	
	public Change getLatestChange() {
		if( changes.size() > 0 )
			return changes.last();
		else
			return null;
	}
	
	public void addChange(Change change) {
		if( change.getFileId().equals(fileId) )
			changes.add(change);
	}
	
	public ChangeSet( URL repositoryUrl, String filePath ) {
		this.changes = new TreeSet<Change>();
		try {
			this.fileId = Change.generateFileId(repositoryUrl, filePath);
		} catch (URISyntaxException e) {
			log.fatal("Exception while generation fileId", e);
		}
	}
	
	public ChangeSet( String fileId ) {
		this.changes = new TreeSet<Change>();
		this.fileId = fileId;
	}
	
	public String getFileId() {
		return fileId;
	}
	
	@Override
	public String toString() {
		return "CS:" + fileId + "-" + changes.size();
	}
	
}
