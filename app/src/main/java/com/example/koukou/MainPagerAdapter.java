package com.example.koukou;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.koukou.ui.contacts.ContactsFragment;
import com.example.koukou.ui.conversations.ConversationsFragment;
import com.example.koukou.ui.settings.SettingsFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new ConversationsFragment();
            case 1:
                return new ContactsFragment();
            case 2:
            default:
                return new SettingsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
