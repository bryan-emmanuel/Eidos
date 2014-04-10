Eidos provides simple  backup of the data directory of an application,
including files, databases, and shared_prefs. An object, DatabaseLock,
is provided for synchronizing database access to avoid data corruption.
Convenience methods are provided for requesting backup and restore.

Register for a backup api key and follow setup instructions here:
http://developer.android.com/training/cloudsync/backupapi.html

Set AndroidManifest.xml to use Eidos for backup:
<application ...
    backupagent="com.piusvelte.eidos.Eidos">

To request a backup, call Eidos.requestBackup(Context context)
