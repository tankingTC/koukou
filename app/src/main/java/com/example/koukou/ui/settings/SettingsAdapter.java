package com.example.koukou.ui.settings;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.koukou.R;
import com.example.koukou.ui.settings.model.SettingsItem;
import com.example.koukou.ui.settings.model.SettingsState;
import com.example.koukou.utils.AppearanceManager;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(SettingsItem item);
    }

    public interface OnSwitchChangedListener {
        void onSwitchChanged(SettingsItem item, boolean isChecked);
    }

    private final List<SettingsItem> items = new ArrayList<>();
    private final OnItemClickListener itemClickListener;
    private final OnSwitchChangedListener switchChangedListener;

    public SettingsAdapter(OnItemClickListener itemClickListener, OnSwitchChangedListener switchChangedListener) {
        this.itemClickListener = itemClickListener;
        this.switchChangedListener = switchChangedListener;
    }

    public void submitList(List<SettingsItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == SettingsItem.TYPE_TITLE) {
            return new TitleHolder(inflater.inflate(R.layout.item_settings_title, parent, false));
        }
        if (viewType == SettingsItem.TYPE_SWITCH) {
            return new SwitchHolder(inflater.inflate(R.layout.item_settings_switch, parent, false));
        }
        if (viewType == SettingsItem.TYPE_ACTION) {
            return new ActionHolder(inflater.inflate(R.layout.item_settings_action, parent, false));
        }
        return new ArrowHolder(inflater.inflate(R.layout.item_settings_arrow, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SettingsItem item = items.get(position);
        if (holder instanceof TitleHolder) {
            ((TitleHolder) holder).titleView.setText(item.title);
        } else if (holder instanceof ArrowHolder) {
            ((ArrowHolder) holder).bind(item);
        } else if (holder instanceof SwitchHolder) {
            ((SwitchHolder) holder).bind(item);
        } else if (holder instanceof ActionHolder) {
            ((ActionHolder) holder).bind(item);
        }
        AppearanceManager.applyItemAppearance(holder.itemView.getContext(), holder.itemView);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private final class TitleHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;

        private TitleHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.tv_title);
        }
    }

    private final class ArrowHolder extends RecyclerView.ViewHolder {
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView valueView;

        private ArrowHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.iv_icon);
            titleView = itemView.findViewById(R.id.tv_title);
            valueView = itemView.findViewById(R.id.tv_value);
        }

        private void bind(SettingsItem item) {
            iconView.setImageResource(item.iconRes);
            titleView.setText(item.title);
            SettingsState state = AppearanceManager.currentState(itemView.getContext());
            boolean minimalWhite = "minimal_white".equals(state.chatBackground);
            boolean matrix = "matrix".equals(state.chatBackground);
            boolean stardust = "stardust".equals(state.chatBackground);
            itemView.setBackgroundResource(minimalWhite
                    ? R.drawable.bg_settings_row_light
                    : (matrix ? R.drawable.bg_settings_row_matrix
                    : (stardust ? R.drawable.bg_settings_row_stardust : R.drawable.bg_settings_row)));
            titleView.setTextColor(Color.parseColor(minimalWhite ? "#162131" : "#F3F6FC"));
            valueView.setTextColor(Color.parseColor(minimalWhite ? "#6A778C" : (matrix ? "#A9C8BE" : (stardust ? "#C6D4EA" : "#B9C2D5"))));
            iconView.setColorFilter(ContextCompat.getColor(itemView.getContext(),
                    minimalWhite ? R.color.butterfly_stroke : R.color.butterfly_cyan));
            itemView.findViewById(R.id.iv_arrow).setAlpha(minimalWhite ? 0.78f : 1f);
            if (item.value == null || item.value.isEmpty()) {
                valueView.setVisibility(View.GONE);
            } else {
                valueView.setVisibility(View.VISIBLE);
                valueView.setText(item.value);
            }
            itemView.setOnClickListener(v -> itemClickListener.onItemClick(item));
        }
    }

    private final class SwitchHolder extends RecyclerView.ViewHolder {
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView valueView;
        private final MaterialSwitch switchView;

        private SwitchHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.iv_icon);
            titleView = itemView.findViewById(R.id.tv_title);
            valueView = itemView.findViewById(R.id.tv_value);
            switchView = itemView.findViewById(R.id.switch_view);
        }

        private void bind(SettingsItem item) {
            SettingsState state = AppearanceManager.currentState(itemView.getContext());
            boolean minimal = "minimal".equals(state.chatBackground) || "minimal_white".equals(state.chatBackground);
            boolean minimalWhite = "minimal_white".equals(state.chatBackground);
            boolean matrix = "matrix".equals(state.chatBackground);
            boolean stardust = "stardust".equals(state.chatBackground);
            itemView.setBackgroundResource(minimalWhite
                    ? R.drawable.bg_settings_row_light
                    : (matrix ? R.drawable.bg_settings_row_matrix
                    : (stardust ? R.drawable.bg_settings_row_stardust : R.drawable.bg_settings_row)));
            iconView.setImageResource(item.iconRes);
            iconView.setColorFilter(ContextCompat.getColor(itemView.getContext(),
                    minimal ? R.color.butterfly_stroke : (stardust ? R.color.butterfly_cyan : R.color.butterfly_purple)));
            titleView.setText(item.title);
            valueView.setText(item.value);
            titleView.setTextColor(Color.parseColor(minimalWhite ? "#162131" : "#F3F6FC"));
            valueView.setTextColor(Color.parseColor(minimalWhite ? "#6A778C" : (matrix ? "#A9C8BE" : (stardust ? "#C6D4EA" : "#B9C2D5"))));
            switchView.setOnCheckedChangeListener(null);
            switchView.setChecked(item.checked);
            int surfaceStrong = ContextCompat.getColor(itemView.getContext(), R.color.butterfly_surface_strong);
            int cyan = ContextCompat.getColor(itemView.getContext(), R.color.butterfly_cyan);
            int cyanTrack = ColorUtils.setAlphaComponent(cyan, minimal ? 132 : (stardust ? 138 : 104));
            int offTrack = Color.parseColor(minimalWhite ? "#C9D8E4" : (matrix ? "#20362F" : (stardust ? "#1A2338" : (minimal ? "#26334A" : "#202845"))));
            int offThumb = Color.parseColor(minimalWhite ? "#EEF4F8" : (matrix ? "#99B8AE" : (stardust ? "#D5E8F8" : (minimal ? "#9EB4C8" : "#A6B0C7"))));
            switchView.setTrackTintList(new android.content.res.ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{cyanTrack, offTrack}
            ));
            switchView.setThumbTintList(new android.content.res.ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{cyan, offThumb}
            ));
            switchView.setElevation(minimal ? 8f : (stardust ? 6f : 2f));
            itemView.setOnClickListener(v -> switchView.setChecked(!switchView.isChecked()));
            switchView.setOnCheckedChangeListener((buttonView, isChecked) -> switchChangedListener.onSwitchChanged(item, isChecked));
        }
    }

    private final class ActionHolder extends RecyclerView.ViewHolder {
        private final TextView actionView;

        private ActionHolder(@NonNull View itemView) {
            super(itemView);
            actionView = itemView.findViewById(R.id.tv_action);
        }

        private void bind(SettingsItem item) {
            SettingsState state = AppearanceManager.currentState(itemView.getContext());
            boolean minimalWhite = "minimal_white".equals(state.chatBackground);
            boolean matrix = "matrix".equals(state.chatBackground);
            boolean stardust = "stardust".equals(state.chatBackground);
            actionView.setBackgroundResource(minimalWhite
                    ? R.drawable.bg_settings_action_row_light
                    : (matrix ? R.drawable.bg_settings_action_row_matrix
                    : (stardust ? R.drawable.bg_settings_action_row_stardust : R.drawable.bg_settings_action_row)));
            actionView.setText(item.title);
            actionView.setTextColor(actionView.getResources().getColor(item.destructive
                    ? R.color.butterfly_danger
                    : (minimalWhite ? android.R.color.black : R.color.butterfly_pearl)));
            itemView.setOnClickListener(v -> itemClickListener.onItemClick(item));
        }
    }
}
