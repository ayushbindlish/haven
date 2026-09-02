/*
 * Copyright (c) 2017 Nathanial Freitas
 *
 *   This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.havenapp.main;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

import org.havenapp.main.database.HavenEventDB;
import org.havenapp.main.service.HousekeepingWorker;
import org.havenapp.main.service.RemoveDeletedFilesWorker;
import org.havenapp.main.service.WebServer;

import java.io.IOException;

public class HavenApp extends MultiDexApplication {


    /*
    ** Onion-available Web Server for optional remote access
     */
    private WebServer mOnionServer = null;

    private PreferenceManager mPrefs = null;

    private static HavenEventDB dataBaseInstance = null;

    private static HavenApp havenApp;

    @Override
    public void onCreate() {
        super.onCreate();

        mPrefs = new PreferenceManager(this);

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        if (mPrefs.getRemoteAccessActive())
            startServer();

        havenApp = this;
        dataBaseInstance = HavenEventDB.getDatabase(this);

        RemoveDeletedFilesWorker.schedule(this);
        HousekeepingWorker.bootstrap(this); // coalesced audit + usage digest, power-adaptive cadence
        org.havenapp.main.service.DeadmanWorker.reschedule(this);
        org.havenapp.main.service.BackupWorker.reschedule(this);
        org.havenapp.main.service.SupervisorWorker.reschedule(this);
        // Auto-arm: re-point the schedule alarm + "away" sampler at the current config.
        org.havenapp.main.autoarm.AutoArmScheduler.sync(this);
        org.havenapp.main.autoarm.AwayWatcher.sync(this);
        // Bring up built-in Tor if it's enabled and something routes through it.
        org.havenapp.main.net.TorController.reconcile(this);
        // Retry any alerts that failed to send before the process last died.
        new org.havenapp.main.alerts.AlertManager(this).flushPending();
    }


    public void startServer ()
    {
        if (mOnionServer == null || (!mOnionServer.isAlive()))
        {
            if ( mPrefs.getRemoteAccessCredential() != null) {
                try {
                    mOnionServer = new WebServer(this, mPrefs.getRemoteAccessCredential());
                } catch (IOException ioe) {
                    Log.e("OnioNServer", "unable to start onion server", ioe);
                }
            }
        }
        org.havenapp.main.net.TorController.reconcile(this);
    }

    public void stopServer ()
    {
        if (mOnionServer != null && mOnionServer.isAlive())
        {
            mOnionServer.stop();
        }
        org.havenapp.main.net.TorController.reconcile(this);
    }

    @NonNull
    public static HavenApp getInstance() {
        return havenApp;
    }

    @NonNull
    public static HavenEventDB getDataBaseInstance() {
        return dataBaseInstance;
    }
}
