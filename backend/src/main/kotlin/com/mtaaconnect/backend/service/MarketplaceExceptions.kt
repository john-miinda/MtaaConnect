package com.mtaaconnect.backend.service

class MarketplaceNotFoundException(message: String) : RuntimeException(message)

class MarketplaceConflictException(message: String) : RuntimeException(message)
