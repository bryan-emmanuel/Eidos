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
import android.text.TextUtils;

import org.w3c.dom.Text;

/**
 * Eidos provides simple backup, including everything under an applications data
 * directory
 *
 * @author bemmanuel
 */
public class Eidos extends BackupAgentHelper {

    private static final String SHARED_PREFS = "shared_prefs";
    private static final String DATABASES = "databases";
    private static final String FILES = "files";
    /** the FileBackupHelper requires files with a path relative to the directory returned from getFilesDir */
    private static final String PATH_PARENT = "..";

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
        final File dataDirectory = new File(getApplicationInfo().dataDir);
        final List<String> backupFiles = new ArrayList<String>();
        final List<String> backupSharedPrefs = new ArrayList<String>();

        if (dataDirectory.exists() && dataDirectory.isDirectory()) {
            final File[] dataFiles = dataDirectory.listFiles();
            if (dataFiles != null) {
                for (File dataFile : dataFiles) {
                    if (dataFile.isDirectory()) {
                        if (SHARED_PREFS.equals(dataFile.getName())) {
                            if (dataFile.isDirectory()) {
                                final File[] preferencesFiles = dataFile.listFiles();
                                if (preferencesFiles != null) {
                                    for (File preferenceFile : preferencesFiles) {
                                        backupSharedPrefs.add(preferenceFile.getName());
                                    }
                                }
                            }
                        } else if (FILES.equals(dataFile.getName())) {
                            addFiles("", dataFile, backupFiles);
                        } else if (DATABASES.equals(dataFile.getName())) {
                            addFiles(PATH_PARENT, dataFile, backupFiles);
                        }
                    }
                }
            }
        }

        addHelper(FILES, new FileBackupHelper(this, backupFiles.toArray(new String[backupFiles.size()])));
        addHelper(SHARED_PREFS, new SharedPreferencesBackupHelper(this, backupSharedPrefs.toArray(new String[backupSharedPrefs.size()])));
    }

    private void addFiles(String relativePath, File file, List<String> backupFiles) {
        if (file != null) {
            if (!TextUtils.isEmpty(relativePath)) relativePath += File.separator;
            relativePath += file.getName();

            if (file.isFile()) {
                backupFiles.add(relativePath);
            } else if (file.isDirectory()) {
                final File[] children = file.listFiles();

                if (children != null) {
                    for (File child : children) {
                        addFiles(relativePath, child, backupFiles);
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
