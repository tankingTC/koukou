package com.example.koukou.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.koukou.R;
import com.example.koukou.theme.ThemePalette;
import com.example.koukou.ui.settings.model.SettingsItem;
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
            ThemePalette palette = AppearanceManager.currentPalette(itemView.getContext());
            itemView.setBackgroundResource(palette.bgSettingsRow);
            titleView.setTextColor(palette.textPrimary);
            valueView.setTextColor(palette.textSecondary);
            iconView.setColorFilter(palette.iconAccent);
            itemView.findViewById(R.id.iv_arrow).setAlpha(palette.lightPalette ? 0.78f : 1f);
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
            ThemePalette palette = AppearanceManager.currentPalette(itemView.getContext());
            itemView.setBackgroundResource(palette.bgSettingsRow);
            iconView.setImageResource(item.iconRes);
            iconView.setColorFilter(palette.iconAccent);
            titleView.setText(item.title);
            valueView.setText(item.value);
            titleView.setTextColor(palette.textPrimary);
            valueView.setTextColor(palette.textSecondary);
            switchView.setOnCheckedChangeListener(null);
            switchView.setChecked(item.checked);
            int cyan = ContextCompat.getColor(itemView.getContext(), R.color.butterfly_cyan);
            int cyanTrack = ColorUtils.setAlphaComponent(cyan, palette.switchActiveAlpha);
            switchView.setTrackTintList(new android.content.res.ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{cyanTrack, palette.switchOffTrack}
            ));
            switchView.setThumbTintList(new android.content.res.ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{cyan, palette.switchOffThumb}
            ));
            switchView.setElevation(palette.switchElevation);
            itemView.setOnClickListener(v -> switchView.setChecked(!switchView.isChecked()));
            switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                switchChangedListener.onSwitchChanged(item, isChecked);
                // If this is the vibration setting and it's turned on, play a short vibration as a sample
                if ("switch_vibration".equals(item.key) && isChecked) {
                    Vibrator vibrator = (Vibrator) itemView.getContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                            } else {
                                vibrator.vibrate(30);
                            }
                        } catch (Throwable t) {
                            // ignore vibration errors
                        }
                    }
                }
            });
        }
    }

    private final class ActionHolder extends RecyclerView.ViewHolder {
        private final TextView actionView;

        private ActionHolder(@NonNull View itemView) {
            super(itemView);
            actionView = itemView.findViewById(R.id.tv_action);
        }

        private void bind(SettingsItem item) {
            ThemePalette palette = AppearanceManager.currentPalette(itemView.getContext());
            actionView.setBackgroundResource(palette.bgSettingsActionRow);
            actionView.setText(item.title);
            if (item.destructive) {
                actionView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.butterfly_danger));
            } else {
                actionView.setTextColor(palette.textPrimary);
            }
            itemView.setOnClickListener(v -> itemClickListener.onItemClick(item));
        }
    }
}
