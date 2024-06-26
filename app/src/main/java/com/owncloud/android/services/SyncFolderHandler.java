/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2020 Chris Narkiewicz <hello@ezaquarii.com>
 * SPDX-FileCopyrightText: 2019 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2016-2018 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2016 ownCloud Inc.
 * SPDX-FileCopyrightText: 2015 María Asensio Valverde <masensio@solidgear.es>
 * SPDX-FileCopyrightText: 2015 David A. Velasco <dvelasco@solidgear.es>
 * SPDX-License-Identifier: GPL-2.0-only AND (AGPL-3.0-or-later OR GPL-2.0-only)
 */
package com.owncloud.android.services;

import android.accounts.Account;
import android.accounts.AccountsException;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;

import com.nextcloud.client.account.User;
import com.nextcloud.client.jobs.download.FileDownloadWorker;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.files.services.IndexedForest;
import com.owncloud.android.lib.common.OwnCloudAccount;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientManagerFactory;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.operations.SynchronizeFolderOperation;

import java.io.IOException;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * SyncFolder worker. Performs the pending operations in the order they were requested.
 * Created with the Looper of a new thread, started in
 * {@link com.owncloud.android.services.OperationsService#onCreate()}.
 */
class SyncFolderHandler extends Handler {

    private static final String TAG = SyncFolderHandler.class.getSimpleName();

    private OperationsService mService;

    private IndexedForest<SynchronizeFolderOperation> mPendingOperations = new IndexedForest<>();

    private Account mCurrentAccount;
    private SynchronizeFolderOperation mCurrentSyncOperation;


    public SyncFolderHandler(Looper looper, OperationsService service) {
        super(looper);
        if (service == null) {
            throw new IllegalArgumentException("Received invalid NULL in parameter 'service'");
        }
        mService = service;
    }

    /**
     * Returns True when the folder located in 'remotePath' in the ownCloud account 'account', or any of its
     * descendants, is being synchronized (or waiting for it).
     *
     * @param user          user where the remote folder is stored.
     * @param remotePath    The path to a folder that could be in the queue of synchronizations.
     */
    public boolean isSynchronizing(User user, String remotePath) {
        if (user == null || remotePath == null) {
            return false;
        }
        return mPendingOperations.contains(user.getAccountName(), remotePath);
    }

    @Override
    public void handleMessage(Message msg) {
        Pair<Account, String> itemSyncKey = (Pair<Account, String>) msg.obj;
        doOperation(itemSyncKey.first, itemSyncKey.second);
        Log_OC.d(TAG, "Stopping after command with id " + msg.arg1);
        mService.stopSelf(msg.arg1);
    }


    /**
     * Performs the next operation in the queue
     */
    private void doOperation(Account account, String remotePath) {

        mCurrentSyncOperation = mPendingOperations.get(account.name, remotePath);

        if (mCurrentSyncOperation != null) {
            RemoteOperationResult result;

            try {

                if (mCurrentAccount == null || !mCurrentAccount.equals(account)) {
                    mCurrentAccount = account;
                }

                // always get client from client manager, to get fresh credentials in case of update
                OwnCloudAccount ocAccount = new OwnCloudAccount(account, mService);
                OwnCloudClient mOwnCloudClient = OwnCloudClientManagerFactory.getDefaultSingleton().
                        getClientFor(ocAccount, mService);

                result = mCurrentSyncOperation.execute(mOwnCloudClient);
                sendBroadcastFinishedSyncFolder(account, remotePath, result.isSuccess());
                mService.dispatchResultToOperationListeners(mCurrentSyncOperation, result);

            } catch (AccountsException | IOException e) {
                sendBroadcastFinishedSyncFolder(account, remotePath, false);
                mService.dispatchResultToOperationListeners(mCurrentSyncOperation, new RemoteOperationResult(e));

                Log_OC.e(TAG, "Error while trying to get authorization", e);
            } finally {
                mPendingOperations.removePayload(account.name, remotePath);
            }
        }
    }

    public void add(Account account, String remotePath,
                    SynchronizeFolderOperation syncFolderOperation){
        Pair<String, String> putResult = mPendingOperations.putIfAbsent(account.name, remotePath, syncFolderOperation);
        if (putResult != null) {
            sendBroadcastNewSyncFolder(account, remotePath);    // TODO upgrade!
        }
    }


    /**
     * Cancels a pending or current sync' operation.
     *
     * @param account       ownCloud {@link Account} where the remote file is stored.
     * @param file          A file in the queue of pending synchronizations
     */
    public void cancel(Account account, OCFile file){
        if (account == null || file == null) {
            Log_OC.e(TAG, "Cannot cancel with NULL parameters");
            return;
        }
        Pair<SynchronizeFolderOperation, String> removeResult = mPendingOperations.remove(account.name,
                                                                                          file.getRemotePath());
        SynchronizeFolderOperation synchronization = removeResult.first;
        if (synchronization != null) {
            synchronization.cancel();
        } else {
            // TODO synchronize?
            if (mCurrentSyncOperation != null && mCurrentAccount != null &&
                mCurrentSyncOperation.getRemotePath().startsWith(file.getRemotePath()) &&
                    account.name.equals(mCurrentAccount.name)) {
                mCurrentSyncOperation.cancel();
            }
        }

        //sendBroadcastFinishedSyncFolder(account, file.getRemotePath());
    }

    /**
     * TODO review this method when "folder synchronization" replaces "folder download";
     * this is a fast and ugly patch.
     */
    private void sendBroadcastNewSyncFolder(Account account, String remotePath) {
        Intent added = new Intent(FileDownloadWorker.Companion.getDownloadAddedMessage());
        added.putExtra(FileDownloadWorker.EXTRA_ACCOUNT_NAME, account.name);
        added.putExtra(FileDownloadWorker.EXTRA_REMOTE_PATH, remotePath);
        added.setPackage(mService.getPackageName());
        LocalBroadcastManager.getInstance(mService.getApplicationContext()).sendBroadcast(added);
    }

    /**
     * TODO review this method when "folder synchronization" replaces "folder download";
     * this is a fast and ugly patch.
     */
    private void sendBroadcastFinishedSyncFolder(Account account, String remotePath,
                                                 boolean success) {
        Intent finished = new Intent(FileDownloadWorker.Companion.getDownloadFinishMessage());
        finished.putExtra(FileDownloadWorker.EXTRA_ACCOUNT_NAME, account.name);
        finished.putExtra(FileDownloadWorker.EXTRA_REMOTE_PATH, remotePath);
        finished.putExtra(FileDownloadWorker.EXTRA_DOWNLOAD_RESULT, success);
        finished.setPackage(mService.getPackageName());
        LocalBroadcastManager.getInstance(mService.getApplicationContext()).sendBroadcast(finished);
    }
}
