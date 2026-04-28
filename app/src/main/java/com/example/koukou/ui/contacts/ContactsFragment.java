package com.example.koukou.ui.contacts;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.koukou.R;
import com.example.koukou.data.repository.ContactRepository;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.FragmentContactsBinding;
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
        View cancelButton = dialogView.findViewById(R.id.btn_cancel);
        View confirmButton = dialogView.findViewById(R.id.btn_confirm);

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
                    Toast.makeText(requireContext(), "添加成功", Toast.LENGTH_SHORT).show();
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
    }

    private void observeAppearance() {
        settingsRepository.getSettingsLiveData().observe(getViewLifecycleOwner(), state -> {
            AppearanceManager.applyNestedPageAppearance(requireContext(), binding.getRoot(), state);
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
