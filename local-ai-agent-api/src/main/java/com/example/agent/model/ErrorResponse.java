package com.example.agent.model;

/** Uniform JSON error body: {"error": "Bad Request", "message": "..."}. */
public record ErrorResponse(String error, String message) {
}
