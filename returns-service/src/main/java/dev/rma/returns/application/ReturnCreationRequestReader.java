package dev.rma.returns.application;

import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.rma.returns.application.exception.InvalidReturnRequestException;
import dev.rma.returns.application.model.CreateReturnCommand;

/** Odczytuje JSON dopiero po sprawdzeniu klucza, zachowując skrót oryginalnych bajtów. */
final class ReturnCreationRequestReader {
    private final ObjectMapper mapper;

    ReturnCreationRequestReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    CreateReturnCommand read(byte[] body) {
        try {
            JsonNode root = mapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(body);
            if (root == null || !root.isObject()) {
                invalid("$", "Request must be a JSON object");
            }
            text(root, "orderId", "orderId");
            text(root, "customerId", "customerId");
            text(root, "orderDeliveredAt", "orderDeliveredAt");
            text(root, "reason", "reason");
            currency(root, "currency");
            optionalNote(root, "customerNote", 1000);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                invalid("items", "At least one item is required");
            }
            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i);
                String path = "items[" + i + "]";
                if (!item.isObject()) {
                    invalid(path, "Item must be an object");
                }
                text(item, "itemId", path + ".itemId");
                text(item, "sku", path + ".sku");
                text(item, "name", path + ".name");
                var quantity = item.path("quantity");
                if (!quantity.isIntegralNumber() || !quantity.canConvertToInt() || quantity.intValue() < 1) {
                    invalid(path + ".quantity", "Quantity must be a positive integer");
                }
                var price = item.path("unitPrice");
                if (!price.isObject()) {
                    invalid(path + ".unitPrice", "Unit price is required");
                }
                var amount = price.path("amount");
                if (!amount.isTextual() || !amount.textValue().matches("[0-9]+\\.[0-9]{2}")) {
                    invalid(
                            path + ".unitPrice.amount",
                            "Amount must be a non-negative decimal string with two decimal places");
                }
                currency(price, path + ".unitPrice.currency");
            }
            return mapper.readerFor(CreateReturnCommand.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .readValue(root);
        } catch (IOException exception) {
            throw new InvalidReturnRequestException(exception);
        }
    }

    private void text(JsonNode object, String name, String path) {
        var value = object.path(name);
        if (!value.isTextual() || value.textValue().isBlank()) {
            invalid(path, "Non-blank text is required");
        }
    }

    private void currency(JsonNode object, String path) {
        text(object, "currency", path);
        if (!object.path("currency").textValue().matches("[A-Z]{3}")) {
            invalid(path, "Use a three-letter ISO currency code");
        }
    }

    private void optionalNote(JsonNode object, String name, int maximum) {
        var value = object.path(name);
        if (!value.isMissingNode()
                && !value.isNull()
                && (!value.isTextual() || value.textValue().length() > maximum)) {
            invalid(name, "Note must be text with at most " + maximum + " characters");
        }
    }

    private void invalid(String field, String message) {
        throw new InvalidReturnRequestException(field, message);
    }
}
