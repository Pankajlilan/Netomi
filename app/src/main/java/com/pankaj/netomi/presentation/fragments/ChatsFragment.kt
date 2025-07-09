package com.pankaj.netomi.presentation.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.pankaj.netomi.R
import com.pankaj.netomi.data.models.Chat
import com.pankaj.netomi.databinding.FragmentChatsBinding
import com.pankaj.netomi.presentation.adapters.ChatAdapter
import com.pankaj.netomi.presentation.viewmodel.ChatViewModel
import com.pankaj.netomi.presentation.viewmodel.NavigationEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatsFragment : Fragment() {
    
    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var chatAdapter: ChatAdapter
    private var isInSelectionMode = false
    private var selectedChats = listOf<Chat>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        setupMenu()
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            onChatClick = { chat ->
                // Navigate to MessagesFragment when chat is clicked
                val action = ChatsFragmentDirections.actionChatsToMessages(chat.id, chat.title)
                findNavController().navigate(action)
            },
            onChatLongClick = { chat ->
                // Enter selection mode on long press
                enterSelectionMode()
            },
            onSelectionChanged = { selectedChatsList ->
                selectedChats = selectedChatsList
                updateSelectionUI()
            }
        )
        
        binding.chatRecyclerView.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }
    
    private fun setupClickListeners() {
        binding.fabNewChat.setOnClickListener {
            viewModel.createNewChat()
        }
    }
    
    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                if (isInSelectionMode) {
                    menuInflater.inflate(R.menu.menu_chat_selection, menu)
                }
            }
            
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_delete -> {
                        showDeleteConfirmationDialog()
                        true
                    }
                    R.id.action_select_all -> {
                        chatAdapter.selectAll()
                        true
                    }
                    android.R.id.home -> {
                        exitSelectionMode()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
    
    private fun enterSelectionMode() {
        isInSelectionMode = true
        chatAdapter.setSelectionMode(true)
        
        // Update action bar to show selection mode
        requireActivity().invalidateOptionsMenu()
        
        // Show floating action button for delete
        binding.fabDelete.visibility = View.VISIBLE
        binding.fabNewChat.visibility = View.GONE
        
        binding.fabDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }
    
    private fun exitSelectionMode() {
        isInSelectionMode = false
        chatAdapter.setSelectionMode(false)
        selectedChats = emptyList()
        
        // Update action bar
        requireActivity().invalidateOptionsMenu()
        
        // Hide delete FAB and show new chat FAB
        binding.fabDelete.visibility = View.GONE
        binding.fabNewChat.visibility = View.VISIBLE
    }
    
    private fun updateSelectionUI() {
        if (isInSelectionMode) {
            val count = selectedChats.size
            if (count == 0) {
                exitSelectionMode()
            } else {
                // Update action bar title with selection count
                requireActivity().title = "$count selected"
                
                // Update delete FAB visibility
                binding.fabDelete.visibility = if (count > 0) View.VISIBLE else View.GONE
            }
        }
    }
    
    private fun showDeleteConfirmationDialog() {
        if (selectedChats.isEmpty()) return
        
        val count = selectedChats.size
        val message = if (count == 1) {
            "Delete this chat?"
        } else {
            "Delete $count chats?"
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Chats")
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ ->
                deleteSelectedChats()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteSelectedChats() {
        viewModel.deleteSelectedChats(selectedChats)
        
        val count = selectedChats.size
        val message = if (count == 1) "Chat deleted" else "$count chats deleted"
        
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        
        exitSelectionMode()
    }
    
    private fun handleBackPressed(): Boolean {
        return if (isInSelectionMode) {
            exitSelectionMode()
            true
        } else {
            false
        }
    }
    
    private fun observeViewModel() {
        // Observe chats
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.chats.collectLatest { chats ->
                chatAdapter.submitList(chats)
                
                // Show empty state if no chats
                if (chats.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.emptyStateText.text = getString(R.string.no_chats_available)
                } else {
                    binding.emptyStateText.visibility = View.GONE
                }
            }
        }
        
        // Observe network connectivity
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isNetworkConnected.collectLatest { isConnected ->
                binding.networkStatus.visibility = if (isConnected) View.GONE else View.VISIBLE
            }
        }
        
        // Observe navigation events for new chat creation
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.navigationEvent.collectLatest { event ->
                when (event) {
                    is NavigationEvent.NavigateToChat -> {
                        // Navigate to the newly created chat's messages screen
                        val action = ChatsFragmentDirections.actionChatsToMessages(
                            chatId = event.chatId,
                            chatTitle = event.title
                        )
                        findNavController().navigate(action)
                    }
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
