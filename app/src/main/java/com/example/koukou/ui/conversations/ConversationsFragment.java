package com.example.koukou.ui.conversations;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.koukou.R;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.FragmentConversationsBinding;
import com.example.koukou.ui.shared.MainViewModelFactory;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.IridescenceAnimator;

public class ConversationsFragment extends Fragment {
    private FragmentConversationsBinding binding;
    private ConversationsViewModel viewModel;
    private ConversationAdapter adapter;
    private boolean hasPlayedListAnimation = false;
    private SettingsRepository settingsRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentConversationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MainViewModelFactory factory = new MainViewModelFactory(requireContext());
        viewModel = new ViewModelProvider(this, factory).get(ConversationsViewModel.class);
        settingsRepository = SettingsRepository.getInstance(requireContext());

        setupRecyclerView();
        observeViewModel();
        observeAppearance();
        viewModel.refreshConversations();

        binding.toolbar.setAlpha(0f);
        binding.toolbar.setTranslationY(-50f);
        binding.toolbar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .start();
        binding.swipeRefresh.setColorSchemeResources(R.color.butterfly_cyan, R.color.butterfly_purple);
        binding.swipeRefresh.setProgressViewOffset(false, 90, 180);
        binding.swipeRefresh.setOnRefreshListener(() -> {
            IridescenceAnimator.triggerRefreshGlitch(binding.ivDecor);
            viewModel.refreshConversations();
            binding.swipeRefresh.postDelayed(() -> {
                binding.swipeRefresh.setRefreshing(false);
                binding.rvConversations.setTranslationY(50f);
                binding.rvConversations.animate().translationY(0f).setDuration(300).start();
            }, 1000);
        });
    }

    private void setupRecyclerView() {
        adapter = new ConversationAdapter(conversation -> {
            viewModel.clearUnreadCount(conversation.conversationId);

            android.content.Intent intent = new android.content.Intent(requireActivity(), com.example.koukou.ui.chat.ChatActivity.class);
            intent.putExtra(com.example.koukou.ui.chat.ChatActivity.EXTRA_TARGET_ID, conversation.targetId);
            intent.putExtra(com.example.koukou.ui.chat.ChatActivity.EXTRA_TARGET_NAME, conversation.targetName);
            intent.putExtra(com.example.koukou.ui.chat.ChatActivity.EXTRA_TARGET_AVATAR, conversation.targetAvatarUrl);
            startActivity(intent);
            requireActivity().overridePendingTransition(com.example.koukou.R.anim.chat_open_enter, com.example.koukou.R.anim.chat_open_exit);
        });
        binding.rvConversations.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvConversations.setHasFixedSize(true);
        binding.rvConversations.setAdapter(adapter);
        binding.rvConversations.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_stagger_in));
    }

    private void observeViewModel() {
        viewModel.getConversations().observe(getViewLifecycleOwner(), conversations -> {
            adapter.submitList(conversations);
            boolean isEmpty = conversations == null || conversations.isEmpty();
            binding.rvConversations.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            binding.tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            AppearanceManager.refreshRecyclerAppearance(requireContext(), binding.rvConversations);
            if (!isEmpty && !hasPlayedListAnimation) {
                hasPlayedListAnimation = true;
                binding.rvConversations.scheduleLayoutAnimation();
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
