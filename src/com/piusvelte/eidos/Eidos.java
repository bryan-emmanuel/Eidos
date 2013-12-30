/*
 * Eidos - Android Backup Helper
 * Copyright (C) 2012 Bryan Emmanuel
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Bryan Emmanuel piusvelte@gmail.com
 */
package com.piusvelte.eidos;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupManager;
import android.app.backup.FileBackupHelper;
import android.app.backup.RestoreObserver;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

/**
 * Eidos provides simple backup, including everything under an applications data
 * directory
 *
 * @author bemmanuel
 *
 */
public class Eidos extends BackupAgentHelper {

	private static final String SHARED_PREFS = "shared_prefs";
	private static final String BACKUP_FILES_KEY = "files";
	private static final String BACKUP_SHARED_PREFS_KEY = SHARED_PREFS;

	/**
	 * DatabaseLock should be used for synchronizing SQLiteDatabase access to
	 * avoid data corruption If no databases are used, this can be ignored.
	 */
	public static final Object DatabaseLock = new Object();

	/**
	 * Collect all SharedPrefs, and other Files, and add them to the backup
	 * helpers
	 */
	@Override
	public void onCreate() {
		final File dataDirectory = Environment.getDataDirectory();
		final List<String> backupFiles = new ArrayList<String>();
		final List<String> backupSharedPrefs = new ArrayList<String>();

		if (dataDirectory != null && dataDirectory.isDirectory()) {
			final File[] dataFiles = dataDirectory.listFiles();

			if (dataFiles != null) {
				for (File dataFile : dataFiles) {
					if (dataFile.isFile()) {
						backupFiles.add(dataFile.getPath());
					} else if (dataFile.isDirectory()) {
						if (SHARED_PREFS.equals(dataFile.getName())) {
							addFiles(dataFile, backupSharedPrefs);
						} else {
							addFiles(dataFile, backupFiles);
						}
					}
				}
			}
		}

		addHelper(BACKUP_FILES_KEY, new FileBackupHelper(this,
				(String[]) backupFiles.toArray()));
		addHelper(BACKUP_SHARED_PREFS_KEY, new SharedPreferencesBackupHelper(
				this, (String[]) backupSharedPrefs.toArray()));
	}

	private void addFiles(File parent, List<String> backupFiles) {
		if (parent != null) {
			if (parent.isFile()) {
				backupFiles.add(parent.getPath());
			} else if (parent.isDirectory()) {
				final File[] children = parent.listFiles();

				if (children != null) {
					for (File child : children) {
						addFiles(child, backupFiles);
					}
				}
			}
		}
	}

	/**
	 * Override onBackup to support synchronizing database access to avoid data
	 * corruption
	 */
	@Override
	public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
			ParcelFileDescriptor newState) throws IOException {
		synchronized (DatabaseLock) {
			super.onBackup(oldState, data, newState);
		}
	}

	/**
	 * Override onRestore to support synchronizing database access to avoid data
	 * corruption
	 */
	@Override
	public void onRestore(BackupDataInput data, int appVersionCode,
			ParcelFileDescriptor newState) throws IOException {
		synchronized (DatabaseLock) {
			super.onRestore(data, appVersionCode, newState);
		}
	}

	/**
	 * Convenience method for requesting a backup
	 *
	 * @param context
	 */
	public static void requestBackup(Context context) {
		(new BackupManager(context.getApplicationContext())).dataChanged();
	}

	/**
	 * Convenience method for requesting a restore, ignoring an observer
	 *
	 * @param context
	 */
	public static void requestRestore(Context context) {
		requestRestore(context, new RestoreObserver() {
		});
	}

	/**
	 * Convenience method for requesting a restore, with an observer
	 *
	 * @param context
	 */
	public static void requestRestore(Context context, RestoreObserver observer) {
		(new BackupManager(context.getApplicationContext()))
				.requestRestore(observer);
	}

}
