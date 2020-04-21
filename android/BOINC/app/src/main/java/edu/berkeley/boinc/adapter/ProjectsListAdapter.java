/*
 * This file is part of BOINC.
 * http://boinc.berkeley.edu
 * Copyright (C) 2020 University of California
 *
 * BOINC is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * BOINC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with BOINC.  If not, see <http://www.gnu.org/licenses/>.
 */
package edu.berkeley.boinc.adapter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.StringUtils;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.List;

import edu.berkeley.boinc.BOINCActivity;
import edu.berkeley.boinc.ProjectsFragment.ProjectsListData;
import edu.berkeley.boinc.R;
import edu.berkeley.boinc.rpc.Notice;
import edu.berkeley.boinc.rpc.Transfer;
import edu.berkeley.boinc.utils.Logging;

public class ProjectsListAdapter extends ArrayAdapter<ProjectsListData> {
    private List<ProjectsListData> entries;
    private Activity activity;

    public ProjectsListAdapter(Activity activity, ListView listView, int textViewResourceId, List<ProjectsListData> entries) {
        super(activity, textViewResourceId, entries);
        this.entries = entries;
        this.activity = activity;

        listView.setAdapter(this);
    }

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public ProjectsListData getItem(int position) {
        return entries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public String getName(int position) {
        return entries.get(position).project.getProjectName();
    }

    private String getUser(int position) {
        String user = entries.get(position).project.getUserName();
        String team = entries.get(position).project.getTeamName();

        if(!team.isEmpty()) {
            return (user + " (" + team + ")");
        }

        return user;
    }

    private Bitmap getIcon(int position) {
        // try to get current client status from monitor
        try {
            return BOINCActivity.monitor.getProjectIcon(entries.get(position).id);
        }
        catch(Exception e) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, "ProjectsListAdapter: Could not load data, clientStatus not initialized.");
            }
            return null;
        }
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ProjectsListData data = entries.get(position);
        boolean isAcctMgr = data.isMgr;

        View vi = convertView;
        // setup new view, if:
        // - view is null, has not been here before
        // - view has different id
        boolean setup = false;
        if(vi == null) {
            setup = true;
        }
        else {
            String viewId = (String) vi.getTag();
            if(!data.id.equals(viewId)) {
                setup = true;
            }
        }

        if(setup) {
            final LayoutInflater layoutInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            assert layoutInflater != null;
            // first time getView is called for this element
            if(isAcctMgr) {
                vi = layoutInflater.inflate(R.layout.projects_layout_listitem_acctmgr, null);
            }
            else {
                vi = layoutInflater.inflate(R.layout.projects_layout_listitem, null);
            }
            //set onclicklistener for expansion
            vi.setOnClickListener(entries.get(position).projectsListClickListener);
            vi.setTag(data.id);
        }

        if(isAcctMgr) {
            // element is account manager

            // populate name
            TextView tvName = vi.findViewById(R.id.name);
            tvName.setText(data.acctMgrInfo.getAcctMgrName());

            // populate url
            TextView tvUrl = vi.findViewById(R.id.url);
            tvUrl.setText(data.acctMgrInfo.getAcctMgrUrl());
        }
        else {
            // element is project
            // set data of standard elements
            TextView tvName = vi.findViewById(R.id.project_name);
            tvName.setText(getName(position));

            TextView tvUser = vi.findViewById(R.id.project_user);
            String userText = getUser(position);
            if(userText.isEmpty()) {
                tvUser.setVisibility(View.GONE);
            }
            else {
                tvUser.setVisibility(View.VISIBLE);
                tvUser.setText(userText);
            }

            String statusText = "";
            try {
                statusText = BOINCActivity.monitor.getProjectStatus(data.project.getMasterURL());
            }
            catch(Exception e) {
                if(Logging.ERROR) {
                    Log.e(Logging.TAG, "ProjectsListAdapter.getView error: ", e);
                }
            }
            TextView tvStatus = vi.findViewById(R.id.project_status);
            if(statusText.isEmpty()) {
                tvStatus.setVisibility(View.GONE);
            }
            else {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText(statusText);
            }

            ImageView ivIcon = vi.findViewById(R.id.project_icon);
            String finalIconId = (String) ivIcon.getTag();
            if(!StringUtils.equals(finalIconId, data.id)) {
                Bitmap icon = getIcon(position);
                // if available set icon, if not boinc logo
                if(icon == null) {
                    // BOINC logo
                    ivIcon.setImageDrawable(getContext().getResources().getDrawable(R.drawable.boinc));
                }
                else {
                    // project icon
                    ivIcon.setImageBitmap(icon);
                    // mark as final
                    ivIcon.setTag(data.id);
                }
            }

            // transfers
            int numberTransfers = data.projectTransfers.size();
            TextView tvTransfers = vi.findViewById(R.id.project_transfers);
            String transfersString = "";
            if(numberTransfers > 0) { // ongoing transfers
                // summarize information for compact representation
                int numberTransfersUpload = 0;
                boolean uploadsPresent = false;
                int numberTransfersDownload = 0;
                boolean downloadsPresent = false;
                boolean transfersActive = false; // true if at least one transfer is active
                long nextRetryS = 0;
                for(Transfer trans : data.projectTransfers) {
                    if(trans.isUpload()) {
                        numberTransfersUpload++;
                        uploadsPresent = true;
                    }
                    else {
                        numberTransfersDownload++;
                        downloadsPresent = true;
                    }
                    if(trans.isTransferActive()) {
                        transfersActive = true;
                    }
                    else if(trans.getNextRequestTime() < nextRetryS || nextRetryS == 0) {
                        nextRetryS = trans.getNextRequestTime();
                    }
                }

                String numberTransfersString = "("; // will never be empty
                if(downloadsPresent) {
                    numberTransfersString +=
                            numberTransfersDownload + " " + activity.getResources().getString(R.string.trans_download);
                }
                if(downloadsPresent && uploadsPresent) {
                    numberTransfersString += " / ";
                }
                if(uploadsPresent) {
                    numberTransfersString +=
                            numberTransfersUpload + " " + activity.getResources().getString(R.string.trans_upload);
                }
                numberTransfersString += ")";

                String activityStatus = ""; // will never be empty
                String activityExplanation = "";
                if(!transfersActive) { // no transfers active, give reason
                    activityStatus += activity.getResources().getString(R.string.trans_pending);

                    if(nextRetryS > 0) { // next try at defined time
                        long retryAtMs = nextRetryS * 1000;
                        long retryInMs = retryAtMs - Calendar.getInstance().getTimeInMillis();
                        if(retryInMs < 0) {
                        }// timestamp in the past, write nothing
                        else {
                            activityExplanation += activity.getResources().getString(R.string.trans_retryin) + " " +
                                                   DateUtils.formatElapsedTime(retryInMs / 1000);
                        }

                    }
                }
                else { // transfers active
                    activityStatus += activity.getResources().getString(R.string.trans_active);
                }

                transfersString +=
                        activity.getResources().getString(R.string.tab_transfers) + " " + activityStatus + " " +
                        numberTransfersString + " " + activityExplanation;
                tvTransfers.setVisibility(View.VISIBLE);
                tvTransfers.setText(transfersString);

            }
            else { // no ongoing transfers
                tvTransfers.setVisibility(View.GONE);
            }

            // credits
            final long userCredit = Math.round(data.project.getUserTotalCredit()),
                    hostCredit = Math.round(data.project.getHostTotalCredit());
            ((TextView) vi.findViewById(R.id.project_credits)).setText(hostCredit == userCredit ?
                                                                       NumberFormat.getIntegerInstance().format(hostCredit) :
                                                                       this.activity.getString(R.string.projects_credits_host_and_user, hostCredit, userCredit));

            // server notice
            Notice notice = data.getLastServerNotice();
            TextView tvNotice = vi.findViewById(R.id.project_notice);
            if(notice == null) {
                tvNotice.setVisibility(View.GONE);
            }
            else {
                tvNotice.setVisibility(View.VISIBLE);
                String noticeText = notice.getDescription().trim();
                tvNotice.setText(noticeText);
            }

            // icon background
            RelativeLayout iconBackground = vi.findViewById(R.id.icon_background);
            if(data.project.getAttachedViaAcctMgr()) {
                iconBackground.setBackground(activity.getApplicationContext().getResources().getDrawable(R.drawable.shape_light_blue_background_wo_stroke));
            }
            else {
                iconBackground.setBackgroundColor(activity.getApplicationContext().getResources().getColor(android.R.color.transparent));
            }
        }

        return vi;
    }
}
