package com.demgames.polypong;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class MiscClasses {

    MiscClasses() {

    }

    static class ClientPlayerArrayAdapter extends ArrayAdapter<IGlobals.Player> {
        public ClientPlayerArrayAdapter(Context context, int resource, List<IGlobals.Player> players) {
            super(context, resource, players);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            IGlobals.Player player = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.clientlistview_row, parent, false);
            }


            // Lookup view for data population
            TextView connectionTextView = (TextView) convertView.findViewById(R.id.connectionTextView);
            // Populate the data into the template view using the data object
            connectionTextView.setText(player.name + " ("+player.ipAdress+")");
            // Return the completed view to render on screen
            return convertView;
        }
    }

    static class ServerArrayAdapter extends ArrayAdapter<IGlobals.Player> {
        public ServerArrayAdapter(Context context, List<IGlobals.Player> players) {
            super(context, 0, players);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            IGlobals.Player player = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.clientlistview_row, parent, false);
            }


            // Lookup view for data population
            TextView connectionTextView = (TextView) convertView.findViewById(R.id.connectionTextView);
            // Populate the data into the template view using the data object
            connectionTextView.setText(player.name + " ("+player.ipAdress+")");
            // Return the completed view to render on screen
            return convertView;
        }
    }

    static class PlayerArrayAdapter extends ArrayAdapter<IGlobals.Player> {
        private int layoutRes;
        private int idRes;
        public PlayerArrayAdapter(Context context, int layoutRes_, int idRes_,  List<IGlobals.Player> players) {
            super(context, 0, players);
            this.layoutRes = layoutRes_;
            this.idRes = idRes_;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            IGlobals.Player player = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(this.layoutRes, parent, false);
            }


            // Lookup view for data population
            TextView connectionTextView = (TextView) convertView.findViewById(this.idRes);
            // Populate the data into the template view using the data object
            connectionTextView.setText(player.name + " ("+player.ipAdress+")");
            // Return the completed view to render on screen
            return convertView;
        }
    }

}