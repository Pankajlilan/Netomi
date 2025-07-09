package com.pankaj.netomi.presentation.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.pankaj.netomi.R
import com.pankaj.netomi.databinding.FragmentMessagesBinding
import com.pankaj.netomi.presentation.adapters.MessageAdapter
import com.pankaj.netomi.presentation.viewmodel.ChatViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MessagesFragment : Fragment() {
    
    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var messageAdapter: MessageAdapter
    private val args: MessagesFragmentArgs by navArgs()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Select the chat when fragment is created
        viewModel.selectChat(args.chatId)
        
        setupRecyclerView()
        setupClickListeners()
        setupTextWatcher()
        observeViewModel()
        setupToolbar()
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = args.chatTitle
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }
    
    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter()
        binding.messageRecyclerView.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                viewModel.sendMessage(message)
                binding.messageInput.setText("")
            }
        }
    }
    
    private fun setupTextWatcher() {
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.sendButton.isEnabled = !s.isNullOrBlank()
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun observeViewModel() {
        // Observe messages
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collectLatest { messages ->
                messageAdapter.submitList(messages) {
                    // Scroll to bottom when new message is added
                    if (messages.isNotEmpty()) {
                        binding.messageRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }
                
                // Show empty state if no messages
                if (messages.isEmpty()) {
                    binding.emptyStateContainer.visibility = View.VISIBLE
                } else {
                    binding.emptyStateContainer.visibility = View.GONE
                }
            }
        }
        
        // Observe network connectivity
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isNetworkConnected.collectLatest { isConnected ->
                binding.networkStatus.visibility = if (isConnected) View.GONE else View.VISIBLE
            }
        }
        
        // Observe UI state for errors
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { uiState ->
                uiState.error?.let { error ->
                    Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG)
                        .setAction("OK") { viewModel.clearError() }
                        .show()
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
