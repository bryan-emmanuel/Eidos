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
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

public class Eidos extends BackupAgentHelper {

	private static final String SHARED_PREFS = "shared_prefs";
	private static final String BACKUP_FILES_KEY = "files";
	private static final String BACKUP_SHARED_PREFS_KEY = SHARED_PREFS;
	private static final String DATABASE = "database";

	public static final Object DatabaseLock = new Object();

	@Override
	public void onCreate() {
		File dataDirectory = Environment.getDataDirectory();
		List<String> backupFiles = new ArrayList<String>();
		List<String> backupSharedPrefs = new ArrayList<String>();

		if (dataDirectory != null && dataDirectory.isDirectory()) {
			File[] dataFiles = dataDirectory.listFiles();

			if (dataFiles != null) {
				for (File dataFile : dataFiles) {
					if (dataFile.isFile()) {
						backupFiles.add(dataFile.getPath());
					} else if (dataFile.isDirectory()) {
						if (SHARED_PREFS.equals(dataFile.getName())) {
							getSharedPrefs(dataFile.getPath(), dataFile,
									backupSharedPrefs);
						} else {
							getBackupFiles(dataFile.getPath(), dataFile,
									backupFiles);
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

	private void getBackupFiles(String parentDirectory, File file,
			List<String> backupFiles) {
		if (file != null) {
			if (file.isFile()) {
				backupFiles.add(parentDirectory + File.separator
						+ file.getName());
			} else if (file.isDirectory()) {
				File[] files = file.listFiles();

				if (files != null) {
					final String currentDirectory = parentDirectory
							+ File.separator + file.getName();

					for (File f : files) {
						getBackupFiles(currentDirectory, f, backupFiles);
					}
				}
			}
		}
	}

	private void getSharedPrefs(String parentDirectory, File sharedPrefs,
			List<String> backupSharedPrefs) {
		File[] files = sharedPrefs.listFiles();

		if (files != null) {

			for (File f : files) {
				backupSharedPrefs.add(parentDirectory + File.separator
						+ f.getName());
			}
		}
	}

	private boolean areDatabasesInUse() {
		File dataDirectory = Environment.getDataDirectory();

		if (dataDirectory != null && dataDirectory.isDirectory()) {
			File[] dataFiles = dataDirectory.listFiles();

			if (dataFiles != null) {

				for (File dataFile : dataFiles) {
					if (DATABASE.equals(dataFile.getName())) {
						if (dataFile.isDirectory()) {
							File[] databases = dataFile.listFiles();

							if (databases != null) {
								for (File database : databases) {

									if (database.isFile()) {
										SQLiteDatabase db = SQLiteDatabase
												.openDatabase(
														database.getPath(),
														null,
														SQLiteDatabase.OPEN_READWRITE);

										if (db.isOpen() && db.isReadOnly()) {
											db.close();

											throw new RuntimeException(
													"Database: "
															+ database
																	.getName()
															+ " in use, unsafe to perform backup functions");
										}

										db.close();
									}
								}
							}
						}

						break;
					}
				}
			}
		}

		return false;
	}

	@Override
	public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
			ParcelFileDescriptor newState) throws IOException {
		synchronized (DatabaseLock) {
			if (!areDatabasesInUse()) {
				super.onBackup(oldState, data, newState);
			}
		}
	}

	@Override
	public void onRestore(BackupDataInput data, int appVersionCode,
			ParcelFileDescriptor newState) throws IOException {
		synchronized (DatabaseLock) {
			if (!areDatabasesInUse()) {
				super.onRestore(data, appVersionCode, newState);
			}
		}
	}

	public static void requestBackup(Context context) {
		(new BackupManager(context.getApplicationContext())).dataChanged();
	}

	public static void requestRestore(Context context) {
		requestRestore(context, new RestoreObserver() {});
	}

	public static void requestRestore(Context context, RestoreObserver observer) {
		(new BackupManager(context.getApplicationContext())).requestRestore(observer);
	}

}
