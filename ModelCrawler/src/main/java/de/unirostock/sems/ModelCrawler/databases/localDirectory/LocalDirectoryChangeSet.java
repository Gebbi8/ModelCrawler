package de.unirostock.sems.ModelCrawler.databases.localDirectory;

import java.net.URL;

import de.unirostock.sems.ModelCrawler.databases.Interface.ChangeSet;

public class LocalDirectoryChangeSet extends ChangeSet {
	
	public LocalDirectoryChangeSet(URL repositoryUrl, String filePath) {
		super(repositoryUrl, filePath);
	}
	
	public void addChange( LocalDirectoryChange change ) {
		super.addChange(change);
	}
	
}
