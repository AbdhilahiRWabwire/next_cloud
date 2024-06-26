/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2015 ownCloud Inc.
 * SPDX-FileCopyrightText: 2015 David A. Velasco <dvelasco@solidgear.es>
 * SPDX-License-Identifier: GPL-2.0-only AND (AGPL-3.0-or-later OR GPL-2.0-only)
 */
package com.owncloud.android.ui.dialog;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nextcloud.utils.extensions.BundleExtensionsKt;
import com.owncloud.android.R;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.ui.activity.CopyToClipboardActivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

/**
 * Dialog showing a list activities able to resolve a given Intent,
 * filtering out the activities matching give package names.
 */
public class ShareLinkToDialog  extends DialogFragment {

    private final static String TAG =  ShareLinkToDialog.class.getSimpleName();
    private final static String ARG_INTENT =  ShareLinkToDialog.class.getSimpleName() +
            ".ARG_INTENT";
    private final static String ARG_PACKAGES_TO_EXCLUDE =  ShareLinkToDialog.class.getSimpleName() +
            ".ARG_PACKAGES_TO_EXCLUDE";

    private ActivityAdapter mAdapter;
    private Intent mIntent;

    public static ShareLinkToDialog newInstance(Intent intent, String... packagesToExclude) {
        ShareLinkToDialog f = new ShareLinkToDialog();
        Bundle args = new Bundle();
        args.putParcelable(ARG_INTENT, intent);
        args.putStringArray(ARG_PACKAGES_TO_EXCLUDE, packagesToExclude);
        f.setArguments(args);
        return f;
    }

    public ShareLinkToDialog() {
        super();
        Log_OC.d(TAG, "constructor");
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mIntent = BundleExtensionsKt.getParcelableArgument(getArguments(), ARG_INTENT, Intent.class);
        String[] packagesToExclude = getArguments().getStringArray(ARG_PACKAGES_TO_EXCLUDE);
        List<String> packagesToExcludeList = Arrays.asList(packagesToExclude != null ?
                packagesToExclude : new String[0]);

        PackageManager pm = getActivity().getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(mIntent, PackageManager.MATCH_DEFAULT_ONLY);
        Iterator<ResolveInfo> it = activities.iterator();
        ResolveInfo resolveInfo;
        while (it.hasNext()) {
            resolveInfo = it.next();
            if (packagesToExcludeList.contains(resolveInfo.activityInfo.packageName.toLowerCase(Locale.ROOT))) {
                it.remove();
            }
        }

        boolean sendAction = mIntent.getBooleanExtra(Intent.ACTION_SEND, false);

        if (!sendAction) {
            // add activity for copy to clipboard
            Intent copyToClipboardIntent = new Intent(getActivity(), CopyToClipboardActivity.class);
            List<ResolveInfo> copyToClipboard = pm.queryIntentActivities(copyToClipboardIntent, 0);
            if (!copyToClipboard.isEmpty()) {
                activities.add(copyToClipboard.get(0));
            }
        }

        Collections.sort(activities, new ResolveInfo.DisplayNameComparator(pm));
        mAdapter = new ActivityAdapter(getActivity(), pm, activities);

        return createSelector(sendAction);
    }

    private AlertDialog createSelector(final boolean sendAction) {

        int titleId;
        if (sendAction) {
            titleId = R.string.activity_chooser_send_file_title;
        } else {
            titleId = R.string.activity_chooser_title;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity())
                    .setTitle(titleId)
                    .setAdapter(mAdapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Add the information of the chosen activity to the intent to send
                            ResolveInfo chosen = mAdapter.getItem(which);
                            ActivityInfo actInfo = chosen.activityInfo;
                            ComponentName name=new ComponentName(
                                actInfo.applicationInfo.packageName,
                                actInfo.name);
                            mIntent.setComponent(name);

                            // Send the file
                            getActivity().startActivity(mIntent);
                        }
        });
        return builder.create();
    }

    class ActivityAdapter extends ArrayAdapter<ResolveInfo> {

        private PackageManager mPackageManager;

        ActivityAdapter(Context context, PackageManager pm, List<ResolveInfo> apps) {
            super(context, R.layout.activity_row, apps);
            this.mPackageManager = pm;
        }

        @Override
        public @NonNull View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = convertView;

            if (view == null) {
                view = newView(parent);
            }
            bindView(position, view);
            return view;
        }

        private View newView(ViewGroup parent) {
            return((LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).
                    inflate(R.layout.activity_row, parent, false);
        }

        private void bindView(int position, View row) {
            TextView label = row.findViewById(R.id.title);
            label.setText(getItem(position).loadLabel(mPackageManager));
            ImageView icon = row.findViewById(R.id.icon);
            icon.setImageDrawable(getItem(position).loadIcon(mPackageManager));
        }
    }
}
