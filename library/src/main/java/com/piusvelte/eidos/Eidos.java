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
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

/**
 * Eidos provides simple backup, including everything under an applications data
 * directory
 *
 * @author bemmanuel
 */
public class Eidos extends BackupAgentHelper {

    private static final String TAG = Eidos.class.getSimpleName();
    private static final String[] BACKUP_PATHS = { "databases", "files" };
    private static final String BACKUP_KEY_SHARED_PREFS = "shared_prefs";
    private static final String BACKUP_KEY_FILES = "files";

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
        final File dataDir = getFilesDir();

        if (dataDir.exists() && dataDir.isDirectory()) {
            addSharedPrefsBackup(dataDir);
            addFilesBackup(dataDir);
        }
    }

    private void addSharedPrefsBackup(File dataDir) {
        final File sharedPrefs = new File(dataDir, BACKUP_KEY_SHARED_PREFS);
        if (sharedPrefs.exists() && sharedPrefs.isDirectory()) {
            final List<String> backupSharedPrefs = new ArrayList<String>();
            final File[] preferences = sharedPrefs.listFiles();
            if (preferences != null) {
                for (File preference : preferences) {
                    // remove the ".xml" extension, as it is added by the SharedPreferencesBackupHelper
                    String name = preference.getName();
                    name = name.substring(0, name.length() - 4);
                    backupSharedPrefs.add(name);
                }
            }

            String[] files = backupSharedPrefs.toArray(new String[backupSharedPrefs.size()]);
            SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this, files);
            addHelper(BACKUP_KEY_SHARED_PREFS, helper);
        }
    }

    private void addFilesBackup(File dataDir) {
        final List<String> backupFiles = new ArrayList<String>();

        for (String path : BACKUP_PATHS) {
            final File backupFile = new File(dataDir, path);
            if (backupFile.exists() && backupFile.isDirectory()) {
                addFiles("", backupFile, backupFiles);
            }
        }

        String[] files = backupFiles.toArray(new String[backupFiles.size()]);
        FileBackupHelper helper = new FileBackupHelper(this, files);
        addHelper(BACKUP_KEY_FILES, helper);
    }

    private void addFiles(String path, File file, List<String> backupFiles) {
        if (!TextUtils.isEmpty(path)) path += File.separator;
        path += file.getName();

        if (file.isFile()) {
            backupFiles.add(path);
        } else if (file.isDirectory()) {
            final File[] children = file.listFiles();

            if (children != null) {
                for (File child : children) {
                    addFiles(path, child, backupFiles);
                }
            }
        }
    }

    @Override
    public File getFilesDir() {
        return new File(getApplicationInfo().dataDir);
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
        requestRestore(context, new RestoreObserver(){});
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
