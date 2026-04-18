# Product Requirements Document (PRD)

## Product: SplitEase (Working Name)

## Platform: Kotlin Multiplatform (KMP)

## Author: Product Management

## Date: April 2026

---

# 1. Overview

SplitEase is a cross-platform mobile application designed to simplify bill splitting among individuals and groups. It enables users to track shared expenses, calculate balances, and settle debts efficiently.

The goal is to eliminate friction in social and group financial interactions while ensuring transparency, accuracy, and ease of use.

---

# 2. Problem Statement

Users frequently encounter challenges when splitting bills:

* Manual calculations are error-prone
* Tracking who owes what becomes messy over time
* Payments across different platforms are fragmented
* Lack of offline-first solutions in emerging markets

---

# 3. Goals & Objectives

## Primary Goals

* Simplify expense splitting
* Enable real-time and offline tracking
* Provide seamless settlement experience

## Secondary Goals

* Build trust through transparency
* Enable cross-currency support
* Optimize for low-connectivity environments

---

# 4. Target Users

## Personas

1. Young professionals (shared expenses, outings)
2. Roommates (rent, utilities)
3. Travelers (group trips)
4. Freelancers/teams (shared project costs)

---

# 5. Key Features

## 5.1 Expense Tracking

* Add expenses (amount, payer, participants)
* Split equally, unequally, or by percentage
* Attach notes or receipts

## 5.2 Group Management

* Create and manage groups
* Invite users via link or phone number
* Track group balances

## 5.3 Settlement System

* Simplified debt calculation
* Suggested minimal transactions
* Integration with payment providers (future scope)

## 5.4 Offline-First Capability

* Local database storage
* Sync when online

## 5.5 Notifications

* Payment reminders
* New expense alerts

## 5.6 Multi-Currency Support

* Real-time exchange rates
* Currency conversion

---

# 6. Technical Requirements

## 6.1 Architecture

* Kotlin Multiplatform (KMP)
* Shared business logic module
* Platform-specific UI (Android/iOS)

## 6.2 Data Layer

* Local DB (SQLDelight / Realm)
* Sync engine for conflict resolution

## 6.3 Backend (Optional but recommended)

* Node.js / Kotlin backend
* REST/GraphQL APIs
* Authentication (JWT/OAuth)

## 6.4 Performance

* Fast load times (<2s)
* Smooth offline usage

## 6.5 Security

* End-to-end encryption (optional future)
* Secure auth and data storage

---

# 7. User Stories

## Core

* As a user, I want to create a group so I can manage shared expenses
* As a user, I want to add an expense so others can see what they owe
* As a user, I want to split bills unequally
* As a user, I want to see my total balance

## Advanced

* As a user, I want offline access to my expenses
* As a user, I want automatic syncing when online
* As a user, I want to settle debts with minimal transactions

---

# 8. UX Principles

* Minimal friction
* Clear financial visibility
* Fast interactions
* Mobile-first design

---

# 9. Metrics for Success

## Business Metrics

* Monthly Active Users (MAU)
* Retention rate (Day 7, Day 30)
* User growth rate

## Product Metrics

* Number of expenses created per user
* Group creation rate
* Settlement completion rate

## Technical Metrics

* App crash rate (<1%)
* Sync success rate (>99%)
* API latency (<300ms)

## User Experience Metrics

* Time to add expense (<10s)
* NPS (Net Promoter Score)

---

# 10. Monetization Strategy

* Freemium model
* Premium features:

  * Advanced analytics
  * Unlimited groups
  * Receipt scanning

---

# 11. Risks & Mitigation

## Risks

* Low adoption due to competition
* Sync conflicts in offline mode
* Payment integration complexity

## Mitigation

* Focus on offline-first advantage
* Strong UX differentiation
* Phased feature rollout

---

# 12. Roadmap

## Phase 1 (MVP)

* Expense tracking
* Group management
* Basic splitting

## Phase 2

* Offline sync
* Notifications

## Phase 3

* Payment integrations
* Multi-currency

---

# 13. Future Enhancements

* AI-based expense categorization
* Smart debt simplification
* Voice input

---

# 14. AI-Friendly Structured Output (.md)

## SYSTEM PROMPT

You are an AI assistant helping build a bill-splitting app.

## CONTEXT

* App: SplitEase
* Platform: Kotlin Multiplatform
* Goal: Simplify bill splitting with offline-first support

## TASKS

* Generate UI screens
* Suggest API contracts
* Optimize sync logic

## CONSTRAINTS

* Must support offline-first
* Must scale globally
* Must handle multi-currency

## OUTPUT FORMAT

* Clean, structured
* Production-ready suggestions

---

---

# 15. System Architecture (High-Level)

## Overview

The system follows an offline-first architecture using Kotlin Multiplatform (KMP) with shared business logic and platform-specific UI.

### Layers

* Presentation Layer (Android/iOS)
* Shared Domain Layer (KMP)
* Data Layer (Local DB + Sync Engine)
* Backend Services (Optional but recommended)

### Key Components

* Sync Engine (conflict resolution, retries)
* Local Database (SQLDelight preferred)
* API Client (Ktor)

---

# 16. Detailed Technical Design

## 16.0 Data Consistency Model (Critical Update)

### Guiding Principle

The system follows a **server-authoritative model with optimistic local UX**, not a pure offline-first model.

### Key Rules

* Server is the **single source of truth** for all financial outcomes
* Local data is considered **temporary and potentially stale**
* UI must reflect **data state (synced vs pending)** at all times

### Data Classification

#### 1. Critical Financial Data (Server Authoritative)

* User balances
* Settlements
* Final expense states

Rules:

* Must always be validated against server
* Cannot be finalized offline without reconciliation
* Must override local state when conflicts occur

#### 2. Non-Critical / Draft Data (Local Friendly)

* Draft expenses
* Pending transactions
* UI state

Rules:

* Can be created offline
* Must be marked as **PENDING**
* Must sync and reconcile with server

### UX State Indicators

* Pending (not synced)
* Syncing
* Synced
* Failed

Users must never confuse pending data with final balances.

---

## 16.1 Offline-First Strategy (Refined)

* All writes go to local DB first (optimistic update)
* Every write is tagged with sync status
* Critical operations may require connectivity or explicit user confirmation

### Important Constraint

* Balance calculations shown offline must be labeled as **"Estimated"** or **"Pending"**

---

## 16.2 Sync Model

Entities:

* User
* Group
* Expense
* Transaction

Each entity includes:

* id (UUID)
* updatedAt (timestamp)
* version (integer)
* syncStatus (PENDING, SYNCED, FAILED)

### Conflict Resolution

* Server wins for all financial data
* Local updates re-applied if still valid
* Last Write Wins only for non-critical fields

---

## 16.3 Data Flow (Updated)

1. User creates expense
2. Saved locally instantly with status = PENDING
3. UI reflects "Pending" state
4. Sync service queues request
5. Backend validates and recalculates balances
6. Server returns authoritative state
7. Local DB updates and UI reconciles

### Important

* UI must gracefully handle corrections from server
* No silent balance inconsistencies

---

## 16.4 Financial Safety Rules

* Never finalize settlements without server confirmation
* Never trust local balance as absolute truth
* All settlements should be recorded as **immutable ledger entries**
* Prevent conflicting offline settlements where possible

---

## 16.5 Terminology (Product Positioning)

The product should be described as:

> "Offline-capable expense tracking with reliable reconciliation"

Not:

* Pure offline-first fintech
* Fully real-time financial system

---

## 16.1 Offline-First Strategy

* All writes go to local DB first
* Sync queue processes operations when network is available
* Conflict resolution strategy: Last Write Wins + Server Authority fallback

## 16.2 Sync Model

Entities:

* User
* Group
* Expense
* Transaction

Each entity includes:

* id (UUID)
* updatedAt (timestamp)
* syncStatus (PENDING, SYNCED, FAILED)

## 16.3 Data Flow

1. User creates expense
2. Saved locally instantly
3. Sync service queues request
4. Backend confirms and resolves conflicts
5. Local DB updated

---

# 17. API Contracts (Sample)

## Create Expense

POST /expenses

```
{
  "groupId": "string",
  "amount": 5000,
  "paidBy": "userId",
  "participants": [
    { "userId": "1", "share": 2500 },
    { "userId": "2", "share": 2500 }
  ],
  "currency": "NGN",
  "note": "Dinner"
}
```

## Get Group Expenses

GET /groups/{groupId}/expenses

## Settle Debt

POST /settlements

---

# 18. Local Database Schema (Simplified)

## Tables

### Users

* id
* name
* phone

### Groups

* id
* name

### Expenses

* id
* groupId
* amount
* paidBy
* createdAt

### ExpenseParticipants

* id
* expenseId
* userId
* share

### SyncQueue

* id
* entityId
* type
* status

---

# 19. Debt Simplification Algorithm

## Goal

Minimize number of transactions required to settle debts.

## Approach

* Calculate net balance per user
* Split into creditors and debtors
* Greedy matching algorithm

Pseudo:

```
while debtors and creditors:
  settle min(debt, credit)
```

---

# 20. UI/UX Wireframe Structure

## Core Screens

### 1. Home Screen

* Total balance
* Recent activity
* Quick add expense

### 2. Group Screen

* Expense list
* Balances per user

### 3. Add Expense Screen

* Amount input
* Split type selector
* Participant selection

### 4. Settlement Screen

* Suggested payments

---

# 21. AI-Optimized Development Prompts

## Generate UI

"Create a clean mobile UI for a bill splitting app with focus on speed and clarity"

## Generate Sync Logic

"Design an offline-first sync engine with retry and conflict resolution"

## Generate API Layer

"Create REST APIs for expense sharing with multi-user support"

---

# 22. Advanced Features (Strategic)

* Smart reminders based on behavior
* Expense prediction (AI)
* OCR receipt scanning
* Voice expense entry

---

# 23. Competitive Strategy

## Competitors

* Splitwise
* Tricount

## Differentiation

* Offline-first (major)
* Faster UX
* Emerging market focus

---

# 24. Go-To-Market Strategy

* Launch in university communities
* Target shared apartments
* Referral incentives

---

# 25. Definition of Success (Expanded)

## Technical Excellence

* Zero data loss
* Seamless sync under poor networks

## Product Excellence

* Users trust calculations completely
* No confusion in balances

## Business Success

* Viral growth via group usage
* High retention via daily utility

---

---

# 26. Technical Architecture Documentation

## 26.1 Architectural Style

The application will follow a combination of:

* Clean Architecture
* MVVM (Model-View-ViewModel)
* Repository Pattern
* Offline-capable, server-authoritative sync model

---

## 26.2 KMP Module Structure

### Shared Module (commonMain)

* Domain models
* Use cases
* Repository interfaces
* Sync engine
* Networking (Ktor)

### Android

* Jetpack Compose
* ViewModels

### iOS

* SwiftUI
* Shared ViewModels

---

## 26.3 Layers

### Presentation

* UI + ViewModels

### Domain

* Business logic

### Data

* Repositories
* Local DB (SQLDelight)
* Remote API

---

## 26.4 Patterns

### Repository Pattern

Abstract data sources

### Use Case Pattern

Encapsulate business logic

### State Management

StateFlow + immutable state

### Sync Queue Pattern

Queue + retry + persistence

---

## 26.5 Sync Engine

Flow:

1. Write locally
2. Queue
3. Sync
4. Reconcile with server

---

## 26.6 Conflict Resolution

* Server wins for financial data
* Version-based detection

---

## 26.7 Data Consistency

* Eventual consistency
* Strong consistency for settlements

---

## 26.8 Error Handling

* Retry network failures
* Clear UI states

---

## 26.9 Security

* HTTPS
* Token auth
* Secure storage

---

## 26.10 Testing

* Unit
* Integration
* UI

---

## 26.11 Observability

* Logs
* Analytics

---

---

# 27. KMP Project Structure (Production-Ready)

```
root
├── shared
│   ├── src/commonMain
│   │   ├── domain
│   │   │   ├── model
│   │   │   ├── repository
│   │   │   └── usecase
│   │   ├── data
│   │   │   ├── local
│   │   │   ├── remote
│   │   │   ├── repository
│   │   │   └── sync
│   │   ├── presentation
│   │   │   └── state
│   │   └── utils
│   ├── src/androidMain
│   └── src/iosMain
│
├── androidApp
│   ├── ui
│   ├── viewmodel
│   └── di
│
├── iosApp
│   ├── UI
│   └── ViewModels
```

---

# 28. Core Interface Definitions (Kotlin)

## Expense Repository

```kotlin
interface ExpenseRepository {
    suspend fun createExpense(expense: Expense): Result<Unit>
    suspend fun getExpenses(groupId: String): Flow<List<Expense>>
    suspend fun syncPending()
}
```

## Sync Manager

```kotlin
interface SyncManager {
    suspend fun enqueue(operation: SyncOperation)
    suspend fun processQueue()
}
```

## Local Data Source

```kotlin
interface LocalDataSource {
    suspend fun insertExpense(expense: ExpenseEntity)
    fun observeExpenses(groupId: String): Flow<List<ExpenseEntity>>
}
```

## Remote Data Source

```kotlin
interface RemoteDataSource {
    suspend fun createExpense(request: CreateExpenseRequest)
    suspend fun fetchExpenses(groupId: String): List<ExpenseDto>
}
```

---

# 29. Sync Engine Skeleton

```kotlin
class SyncManagerImpl(
    private val queueDao: SyncQueueDao,
    private val api: RemoteDataSource
) : SyncManager {

    override suspend fun enqueue(operation: SyncOperation) {
        queueDao.insert(operation.toEntity())
    }

    override suspend fun processQueue() {
        val items = queueDao.getPending()

        items.forEach { item ->
            try {
                when (item.type) {
                    SyncType.CREATE_EXPENSE -> {
                        api.createExpense(item.payload)
                    }
                }
                queueDao.markSynced(item.id)
            } catch (e: Exception) {
                queueDao.markFailed(item.id)
            }
        }
    }
}
```

---

# 30. ViewModel Pattern (KMP Shared)

```kotlin
class ExpenseViewModel(
    private val getExpenses: GetExpensesUseCase,
    private val createExpense: CreateExpenseUseCase
) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun load(groupId: String) {
        getExpenses(groupId)
            .onEach {
                _state.update { s -> s.copy(data = it) }
            }
            .launchIn(scope)
    }

    fun addExpense(input: ExpenseInput) {
        scope.launch {
            createExpense(input)
        }
    }
}
```

---

# 31. Dependency Injection Strategy

Recommended:

* Koin (lightweight for KMP)

Example:

```kotlin
val appModule = module {
    single<ExpenseRepository> { ExpenseRepositoryImpl(get(), get(), get()) }
    single<SyncManager> { SyncManagerImpl(get(), get()) }
}
```

---

# 32. Advanced Sync Considerations

* Exponential backoff retries
* Network state awareness
* Background workers (WorkManager / iOS equivalent)
* Partial sync batching

---

# 33. Real-Time Strategy

Phase 1:

* Polling

Phase 2:

* WebSockets

Important:

* Always reconcile with server snapshot

---

# 34. Engineering Principles

* Single source of truth = backend
* UI reflects state, not assumptions
* Every write is traceable
* Prefer simplicity over premature optimization

---

# END OF DOCUMENT
