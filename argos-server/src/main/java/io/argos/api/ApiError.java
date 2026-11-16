package io.argos.api;

/** Erreur restituée au client : message et, le cas échéant, attribut en cause. */
public record ApiError(String error, String attribute) {
}
