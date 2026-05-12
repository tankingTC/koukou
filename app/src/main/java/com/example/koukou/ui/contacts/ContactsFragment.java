package com.example.koukou.ui.contacts;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.koukou.R;
import com.example.koukou.data.local.entity.FriendRequestEntity;
import com.example.koukou.data.repository.ContactRepository;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.FragmentContactsBinding;
import com.example.koukou.theme.ThemePalette;
import com.example.koukou.ui.shared.MainViewModelFactory;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.IridescenceAnimator;
import com.example.koukou.utils.UserHelper;

public class ContactsFragment extends Fragment {
    private FragmentContactsBinding binding;
    private ContactsViewModel viewModel;
    private ContactAdapter adapter;
    private boolean hasPlayedListAnimation = false;
    private SettingsRepository settingsRepository;
    private int pendingRequestCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentContactsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MainViewModelFactory factory = new MainViewModelFactory(requireContext());
        viewModel = new ViewModelProvider(this, factory).get(ContactsViewModel.class);
        settingsRepository = SettingsRepository.getInstance(requireContext());

        setupRecyclerView();
        observeViewModel();
        observeAppearance();

        binding.toolbar.setAlpha(0f);
        binding.toolbar.setTranslationY(-50f);
        binding.toolbar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .start();
        binding.ivAddFriend.setOnClickListener(v -> {
            v.animate().cancel();
            v.setScaleX(0.86f);
            v.setScaleY(0.86f);
            v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1f))
                    .withEndAction(this::showAddFriendDialog)
                    .start();
        });
    }

    private void showAddFriendDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_friend, null, false);
        EditText input = dialogView.findViewById(R.id.et_target_id);
        TextView requestsButton = dialogView.findViewById(R.id.btn_requests);
        View cancelButton = dialogView.findViewById(R.id.btn_cancel);
        View confirmButton = dialogView.findViewById(R.id.btn_confirm);
        requestsButton.setText(pendingRequestCount > 0 ? "申请(" + pendingRequestCount + ")" : "申请");

        AppearanceManager.applyEffectState(
                dialogView.findViewById(R.id.dialog_halo),
                dialogView.findViewById(R.id.dialog_sheen),
                null,
                AppearanceManager.currentState(requireContext()),
                () -> {
                    IridescenceAnimator.startHaloPulse(dialogView.findViewById(R.id.dialog_halo));
                    IridescenceAnimator.startSheenDrift(dialogView.findViewById(R.id.dialog_sheen), 0f, -20f, 0f, 12f, 0.14f, 0.28f);
                    IridescenceAnimator.startButtonGlow(confirmButton);
                }
        );
        AppearanceManager.applyItemAppearance(requireContext(), dialogView);

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.setOnShowListener(d -> {
            IridescenceAnimator.startPanelBounceIn(dialogView);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        requestsButton.setOnClickListener(v -> showFriendRequestsDialog());
        confirmButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            String targetId = input.getText().toString().trim();
            if (targetId.isEmpty()) {
                input.setError("请输入扣扣号");
                return;
            }
            if (targetId.length() != 10) {
                input.setError("扣扣号必须是 10 位数字");
                return;
            }

            viewModel.addFriend(UserHelper.getUserId(requireContext()), targetId, new ContactRepository.Callback() {
                @Override
                public void onSuccess() {
                    Toast.makeText(requireContext(), "好友申请已发送", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }

                @Override
                public void onError(String msg) {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void setupRecyclerView() {
        adapter = new ContactAdapter();
        binding.rvContacts.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvContacts.setHasFixedSize(true);
        binding.rvContacts.setAdapter(adapter);
        binding.rvContacts.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_stagger_in));
    }

    private void observeViewModel() {
        viewModel.getContacts().observe(getViewLifecycleOwner(), contacts -> {
            adapter.submitList(contacts);
            AppearanceManager.refreshRecyclerAppearance(requireContext(), binding.rvContacts);
            if (contacts != null && !contacts.isEmpty() && !hasPlayedListAnimation) {
                hasPlayedListAnimation = true;
                binding.rvContacts.scheduleLayoutAnimation();
            }
        });
        viewModel.getIncomingRequests().observe(getViewLifecycleOwner(), requests -> {
        });
        viewModel.getPendingIncomingCount().observe(getViewLifecycleOwner(), count -> {
            pendingRequestCount = count == null ? 0 : count;
            binding.ivAddFriend.setContentDescription(pendingRequestCount > 0 ? "好友申请 " + pendingRequestCount + " 条" : "添加好友");
        });
    }

    private void showFriendRequestsDialog() {
        ThemePalette palette = AppearanceManager.currentPalette(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(32, 28, 32, 28);
        content.setBackgroundResource(palette.bgGlassCard);

        TextView title = new TextView(requireContext());
        title.setText("好友申请");
        title.setTextSize(20f);
        title.setTextColor(palette.textPrimary);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        content.addView(title);

        java.util.List<FriendRequestEntity> requests = viewModel.getIncomingRequests().getValue();
        if (requests == null || requests.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("暂无好友申请");
            empty.setTextSize(14f);
            empty.setTextColor(palette.textSecondary);
            empty.setPadding(0, 24, 0, 8);
            content.addView(empty);
        } else {
            for (FriendRequestEntity request : requests) {
                content.addView(createRequestItem(request, palette));
            }
        }

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(scrollView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private View createRequestItem(FriendRequestEntity request, ThemePalette palette) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, 22, 0, 0);

        TextView name = new TextView(requireContext());
        name.setText((request.fromNickname == null || request.fromNickname.isEmpty() ? request.fromUserId : request.fromNickname) + " 请求添加你为好友");
        name.setTextSize(15f);
        name.setTextColor(palette.textPrimary);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        item.addView(name);

        TextView message = new TextView(requireContext());
        message.setText((request.message == null || request.message.isEmpty() ? "请求添加你为好友" : request.message) + " · " + requestStatusLabel(request.status));
        message.setTextSize(13f);
        message.setTextColor(palette.textSecondary);
        message.setPadding(0, 6, 0, 0);
        item.addView(message);

        if ("pending".equals(request.status)) {
            LinearLayout actions = new LinearLayout(requireContext());
            actions.setGravity(android.view.Gravity.END);
            actions.setPadding(0, 12, 0, 0);

            TextView reject = new TextView(requireContext());
            reject.setText("拒绝");
            reject.setGravity(android.view.Gravity.CENTER);
            reject.setMinWidth(90);
            reject.setHeight(42);
            reject.setTextColor(palette.textPrimary);
            reject.setBackgroundResource(R.drawable.bg_profile_btn_outline);
            reject.setOnClickListener(v -> viewModel.rejectFriendRequest(request.requestId, requestCallback("已拒绝好友申请")));
            actions.addView(reject);

            TextView accept = new TextView(requireContext());
            accept.setText("同意");
            accept.setGravity(android.view.Gravity.CENTER);
            accept.setMinWidth(90);
            accept.setHeight(42);
            accept.setTextColor(Color.WHITE);
            accept.setBackgroundResource(R.drawable.bg_button_gradient_20);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(14);
            accept.setLayoutParams(params);
            accept.setOnClickListener(v -> viewModel.acceptFriendRequest(request.requestId, requestCallback("已添加好友")));
            actions.addView(accept);
            item.addView(actions);
        }
        return item;
    }

    private ContactRepository.Callback requestCallback(String successMessage) {
        return new ContactRepository.Callback() {
            @Override
            public void onSuccess() {
                Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String msg) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        };
    }

    private String requestStatusLabel(String status) {
        if ("accepted".equals(status)) {
            return "已同意";
        }
        if ("rejected".equals(status)) {
            return "已拒绝";
        }
        return "待处理";
    }

    private void observeAppearance() {
        settingsRepository.getSettingsLiveData().observe(getViewLifecycleOwner(), state -> {
            AppearanceManager.applyNestedPageAppearance(requireContext(), binding.getRoot(), state);
            ThemePalette palette = AppearanceManager.paletteOf(state);
            binding.toolbar.setTitleTextColor(palette.textPrimary);
            binding.ivAddFriend.setBackgroundResource(palette.bgPanel);
            binding.ivAddFriend.setColorFilter(palette.iconPrimary);
            binding.glowHalo.setVisibility(View.GONE);
            binding.glowSheen.setVisibility(View.GONE);
            AppearanceManager.applyEffectState(binding.glowHalo, binding.glowSheen, binding.ivDecor, state, () -> {
                IridescenceAnimator.startHeroFloat(binding.ivDecor);
                IridescenceAnimator.startDreamscape(binding.ivDecor);
            });
            if (state == null || state.immersiveEffectsEnabled) {
                IridescenceAnimator.startButtonGlow(binding.ivAddFriend);
            } else {
                IridescenceAnimator.stopEffects(binding.ivAddFriend);
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
