# Netomi
![app_icon (1)](https://github.com/user-attachments/assets/83a45164-ebb3-413b-9fa6-8f280fe7fd2e)

Netomi Chat App is an AI-powered messaging platform with real-time communication and offline functionality.
=======



## Screenshots

| Dashboard Chat Screen  |  Messages Screen   | Single/Multiple Chat Delete Screen |
|--------------|-----------------|-----------------|
|     <img width="430" alt="SS3" src="https://github.com/user-attachments/assets/5d37c73a-db38-4a23-8d0a-fb8f77a634f4" /> |  <img width="430" alt="SS3" src="https://github.com/user-attachments/assets/22d878f1-29bc-41dd-8242-a1721f1bdd8c" /> | <img width="430" alt="SS3" src="https://github.com/user-attachments/assets/9552eb9f-480f-4d2c-8e5b-cb98a19329b7" /> |

| Offline Message Screen  |  Offline Chat Screen   | Empty Message Screen |
|--------------|-----------------|-----------------|
|     <img width="434" alt="SS4" src="https://github.com/user-attachments/assets/8713b458-290f-45eb-ae07-b587f119ef1b" /> |  <img width="435" alt="SS5" src="https://github.com/user-attachments/assets/d57c10ff-7485-4234-9628-a449ddc36a09" /> | <img width="434" alt="SS4" src="https://github.com/user-attachments/assets/6de789ff-e10a-4649-b92d-b8bc301343f8" /> |

### 🎥 Demo
Netomi Demo: https://drive.google.com/file/d/1NeyL4QLNxgrnUX-v8yEm8MgOTJRYIOUJ/view?usp=sharing

Multiple Chat Deletion Demo: https://drive.google.com/file/d/1v9juA1b93D_CyHIs-3OW_gYZHwVQgy8A/view?usp=sharing

## Features

### ✅ Core Requirements Implemented

#### Chat Interface
- **Single-screen design** with chat list and message view
- **List of chatbot conversations** with preview of latest messages
- **Modern UI** with Material Design components and CardViews

#### Real-Time Syncing (P0)
- **Socket.IO integration** for real-time communication using pie.host
- **Instant message updates** without manual refreshing
- **Connection status monitoring** with visual indicators
- **Automatic reconnection** when connection is restored

#### Offline Functionality (P0)
- **Message queuing** - Messages that fail to send are automatically queued
- **Automatic retry** - Queued messages are retried when device comes back online
- **WorkManager integration** for background message retry
- **Persistent storage** using Room database for offline message storage

#### Error Handling
- **Clear error alerts** for API and network failures using Snackbars
- **Empty state handling** for:
  - No chats available
  - No messages in selected chat
  - No internet connection (visual indicator)

#### Chat Preview & Navigation (P1, P2)
- **✅ P1: Unread message previews** - Shows unread count badges for each chat
- **✅ P2: Individual chat switching** - Ability to switch between different chat conversations
- **Message status indicators** - Sent, delivered, and pending states
- **Timestamp formatting** for messages and chats

## Technical Architecture

### Clean Architecture Pattern
- **Data Layer**: Repository pattern with Room database and Socket.IO
- **Domain Layer**: Use cases and models
- **Presentation Layer**: MVVM with ViewModels and data binding

### Key Technologies
- **Kotlin** - Primary development language
- **Room Database** - Local data persistence
- **Socket.IO Client** - Real-time communication
- **Dagger Hilt** - Dependency injection
- **Coroutines & Flow** - Asynchronous programming
- **WorkManager** - Background task management
- **Data Binding** - UI binding
- **Material Design** - UI components

## Project Structure

```
app/src/main/java/com/pankaj/netomi/
├── data/
│   ├── database/
│   │   ├── ChatDatabase.kt
│   │   ├── ChatDao.kt
│   │   └── ChatMessageDao.kt
│   ├── models/
│   │   ├── Chat.kt
│   │   └── ChatMessage.kt
│   ├── network/
│   │   ├── SocketService.kt
│   │   └── NetworkConnectivityManager.kt
│   ├── repository/
│   │   └── ChatRepository.kt
│   └── work/
│       └── MessageRetryWorker.kt
├── presentation/
│   ├── adapters/
│   │   ├── ChatAdapter.kt
│   │   └── MessageAdapter.kt
│   └── viewmodel/
│       └── ChatViewModel.kt
├── hilt/
│   ├── DatabaseModule.kt
│   └── ApplicationModule.kt
├── common/
│   └── BindingAdapters.kt
├── MainActivity.kt
└── BaseApplication.kt
```

## Setup Instructions

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 23 or higher
- Kotlin 1.5.0 or later

### Installation Steps

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Netomi
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the project folder and open it

3. **Build the project**
   ```bash
   ./gradlew build
   ```

4. **Run the application**
   - Connect an Android device or start an emulator
   - Click "Run" in Android Studio or use:
   ```bash
   ./gradlew installDebug
   ```

## How to Use

### Getting Started
1. **Launch the app** - The app opens with an empty chat list
2. **Create a new chat** - Tap the floating action button (+) to create a new chatbot conversation
3. **Send messages** - Type in the message input field and tap "Send"
4. **Receive responses** - The chatbot will automatically respond to your messages
5. **Switch between chats** - Tap on any chat in the list to switch conversations

### Key Features in Action

#### Real-Time Communication
- Messages appear instantly in the chat
- Connection status is shown at the top when offline
- Messages are queued and retried automatically when connection is restored

#### Offline Functionality
- Turn off internet connection and send messages
- Messages will show as "pending" with a clock icon
- Turn internet back on - messages will be sent automatically
- Visual feedback shows when messages are successfully sent

#### Chat Management
- Create multiple chat conversations
- Each chat shows the latest message preview
- Unread message counts are displayed as badges
- All chats are cleared when the app is closed (as required)

## Testing the Application

### Manual Testing Scenarios

1. **Real-time messaging**
   - Send messages and verify instant delivery
   - Check message status indicators

2. **Offline functionality**
   - Disable internet connection
   - Send messages (they should queue)
   - Re-enable internet
   - Verify messages are sent automatically

3. **Chat management**
   - Create multiple chats
   - Switch between them
   - Verify message history is maintained

4. **Error handling**
   - Test with poor network conditions
   - Verify error messages appear
   - Test empty states

## Dependencies

### Core Dependencies
```gradle
// Socket.IO for real-time communication
implementation 'io.socket:socket.io-client:2.0.0'

// Room database for offline storage
implementation "androidx.room:room-runtime:2.3.0"
implementation "androidx.room:room-ktx:2.3.0"

// WorkManager for background message retry
implementation "androidx.work:work-runtime-ktx:2.7.1"

// Coroutines for asynchronous programming
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0'

// Dagger Hilt for dependency injection
implementation "com.google.dagger:hilt-android:2.38.1"
```

## Known Limitations

1. **Socket Server**: Currently configured for pie.host - may need adjustment for production use
2. **Bot Responses**: Simulated responses for demo purposes
3. **Message Encryption**: Not implemented (would be needed for production)
4. **Push Notifications**: Not implemented
5. **User Authentication**: Not implemented (single user experience)

## Future Enhancements

1. **User Authentication** - Multi-user support
2. **Push Notifications** - Background message notifications
3. **Message Encryption** - End-to-end encryption
4. **File Sharing** - Image and file message support
5. **Voice Messages** - Audio message support
6. **Chat Export** - Export chat history
7. **Dark Mode** - Theme support
8. **Message Search** - Search within chats

## Support

For any issues or questions, please refer to the code documentation or create an issue in the project repository.

## License

This project is developed as a demo application for showcasing real-time chat functionality with offline capabilities.
