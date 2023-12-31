package com.pbuczek.pf.it;

import com.pbuczek.pf.apikey.ApiKey;
import com.pbuczek.pf.apikey.ApiKeyRepository;
import com.pbuczek.pf.user.PaymentPlan;
import com.pbuczek.pf.user.User;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.*;

import static com.pbuczek.pf.apikey.ApiKeyController.API_KEY_LIMITS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("IntegrationTest")
public class ApiKeyIT extends _BaseIT {

    private final Set<String> createdApiKeyIdentifiers = new HashSet<>();

    @Autowired
    private ApiKeyRepository apiRepo;

    @AfterEach
    void tearDown() {
        createdApiKeyIdentifiers.forEach(identifier -> apiRepo.deleteApiKeyByIdentifier(identifier));
        createdUserIds.forEach(id -> userRepo.deleteUser(id));
    }


    @Test
    void apiKeyIsCreatedCorrectly() {
        int userId = createUser(TEST_USERNAME_STANDARD_1, TEST_EMAIL_STANDARD_1);
        enableUserAccount(userId);
        changeUserPaymentPlan(userId, PaymentPlan.ADVENTURER);

        String apiKeyReceivedPass = createApiKey(HttpStatus.OK);
        assertThat(apiKeyReceivedPass).hasSize(71);

        ApiKey apiKey = getApiKeyFromRepo(apiKeyReceivedPass);

        assertAll("Verify ApiKey properties",
                () -> assertThat(apiKey.getUserId()).isEqualTo(userId),
                () -> assertThat(apiKey.getValidTillDate()).isBeforeOrEqualTo(LocalDate.now().plusYears(1)),
                () -> assertThat(apiKey.getIdentifier()).isNotEmpty().isAlphanumeric().hasSize(ApiKey.IDENTIFIER_LENGTH)
                        .doesNotContainAnyWhitespaces().hasLineCount(1),
                () -> assertThat(apiKey.getApiKeyValue())
                        .doesNotContainAnyWhitespaces().hasLineCount(1));
    }

    @Test
    void apiKeyCanBeDeleted() {
        int userId = createUser(TEST_USERNAME_STANDARD_1, TEST_EMAIL_STANDARD_1);
        enableUserAccount(userId);
        changeUserPaymentPlan(userId, PaymentPlan.ADVENTURER);

        String apiKeyReceivedPass = createApiKey(HttpStatus.OK);

        deleteApiKey(userId, apiKeyReceivedPass.substring(0, ApiKey.IDENTIFIER_LENGTH));

        Optional<ApiKey> optionalApiKey =
                apiRepo.findByIdentifier(apiKeyReceivedPass.substring(0, ApiKey.IDENTIFIER_LENGTH));
        assertThat(optionalApiKey).isEmpty();
    }


    @Test
    void apiKeysCannotBeCreatedOverLimit() {
        int userId = createUser(TEST_USERNAME_STANDARD_1, TEST_EMAIL_STANDARD_1);
        enableUserAccount(userId);

        API_KEY_LIMITS.forEach((paymentPlan, limit) -> {
            changeUserPaymentPlan(userId, paymentPlan);
            for (int i = 0; i < limit; i++) {
                apiRepo.save(new ApiKey(UUID.randomUUID().toString(), userId));
            }
            createApiKey(HttpStatus.BAD_REQUEST);

            List<ApiKey> apiKeysList = apiRepo.findByUserId(userId);

            apiKeysList.forEach(apiKey -> apiRepo.deleteApiKeyByIdentifier(apiKey.getIdentifier()));
        });
    }

    @Test
    void apiKeysCanBeFoundByUserId() {
        int userId = createUser(TEST_USERNAME_STANDARD_1, TEST_EMAIL_STANDARD_1);
        enableUserAccount(userId);
        changeUserPaymentPlan(userId, PaymentPlan.ADVENTURER);
        String apiKeyPass1 = createApiKey(HttpStatus.OK);
        String apiKeyPass2 = createApiKey(HttpStatus.OK);

        MockHttpServletResponse response =
                sendRequest(HttpMethod.GET, HttpStatus.OK, TEST_USERNAME_STANDARD_1, "/apikey/" + userId, "");

        List<ApiKey> listOfApiKeys = readListFromResponse(response, ApiKey.class);

        assertThat(listOfApiKeys).hasSize(2);
        listOfApiKeys.forEach(apiKey -> assertThat(apiKey.getIdentifier())
                .containsAnyOf(apiKeyPass1.substring(0, ApiKey.IDENTIFIER_LENGTH),
                        apiKeyPass2.substring(0, ApiKey.IDENTIFIER_LENGTH)));
    }

    @Test
    void apiKeyValidTillDateCanBeReceived() {
        int userId = createUser(TEST_USERNAME_STANDARD_1, TEST_EMAIL_STANDARD_1);
        enableUserAccount(userId);
        changeUserPaymentPlan(userId, PaymentPlan.ADVENTURER);
        String apiKeyPass = createApiKey(HttpStatus.OK);
        String url = "/apikey/valid-till-date/" + userId + "/" + apiKeyPass.substring(0, ApiKey.IDENTIFIER_LENGTH);

        MockHttpServletResponse response =
                sendRequest(HttpMethod.GET, HttpStatus.OK, TEST_USERNAME_STANDARD_1, url, "");

        LocalDate date = readObjectFromResponse(response, LocalDate.class);
        assertThat(date).isAfterOrEqualTo(LocalDate.now().plusDays(364))
                .isBeforeOrEqualTo(LocalDate.now().plusYears(1));
    }

    @SneakyThrows
    @Test
    void apiKeyCanBeUsedToAuthorizeGetUserRequest() {
        int userId = createUser(TEST_USERNAME_STANDARD_1, TEST_EMAIL_STANDARD_1);
        enableUserAccount(userId);
        changeUserPaymentPlan(userId, PaymentPlan.ADVENTURER);
        String apiKey = createApiKey(HttpStatus.OK);

        MockHttpServletResponse response = this.mockMvc.perform(MockMvcRequestBuilders
                        .request(HttpMethod.GET, "/user/by-userid/" + userId)
                        .header("X-API-KEY", apiKey))
                .andExpect(status().is(HttpStatus.OK.value())).andReturn().getResponse();
        assertThat(readObjectFromResponse(response, User.class).getId()).isEqualTo(userId);
    }

    @SneakyThrows
    @Test
    void apiKeyCannotBeUsedToAuthorizeChangeUsername() {
        int userId = createUser(TEST_USERNAME_STANDARD_1, TEST_EMAIL_STANDARD_1);
        enableUserAccount(userId);
        changeUserPaymentPlan(userId, PaymentPlan.ADVENTURER);
        String apiKey = createApiKey(HttpStatus.OK);

        MockHttpServletResponse response = this.mockMvc.perform(MockMvcRequestBuilders
                        .request(HttpMethod.PATCH, "/user/username/" + userId + "/" + TEST_USERNAME_STANDARD_2)
                        .header("X-API-KEY", apiKey))
                .andExpect(status().is(HttpStatus.FORBIDDEN.value())).andReturn().getResponse();
        assertThat(response.getErrorMessage()).isEqualTo("Forbidden. Cannot use API Key for this action.");
    }

    @Test
    void userWithApiKeysCanBeDeletedAndKeysGetDeletedWithHim() {
        Integer userId = readObjectFromResponse(
                createUser(TEST_USERNAME_STANDARD_1, TEST_EMAIL_STANDARD_1, HttpStatus.OK), User.class).getId();
        enableUserAccount(userId);
        changeUserPaymentPlan(userId, PaymentPlan.ADVENTURER);

        String apiKeyReceivedPass1 = createApiKey(HttpStatus.OK);
        String apiKeyReceivedPass2 = createApiKey(HttpStatus.OK);

        assertThat(deleteUser(userId)).isEqualTo(1);

        for (String apiKeyPass : List.of(apiKeyReceivedPass1, apiKeyReceivedPass2)) {
            assertThat(apiRepo.findByIdentifier(apiKeyPass.substring(0, ApiKey.IDENTIFIER_LENGTH))).isEmpty();
        }
    }


    @SneakyThrows
    private String createApiKey(HttpStatus expectedStatus) {
        MockHttpServletResponse response = sendRequest(HttpMethod.POST, expectedStatus, TEST_USERNAME_STANDARD_1,
                "/apikey", "");

        String apiKeyReceivedPass = response.getContentAsString();
        try {
            if (!apiKeyReceivedPass.isBlank()) {
                createdApiKeyIdentifiers.add(apiKeyReceivedPass.substring(0, ApiKey.IDENTIFIER_LENGTH));
            }
        } catch (Exception ignored) {
        }

        return apiKeyReceivedPass;
    }

    @SneakyThrows
    private void deleteApiKey(Integer userId, String identifier) {
        this.mockMvc.perform(delete("/apikey/" + userId + "/" + identifier)
                        .header("Authorization", getBasicAuthenticationHeader(TEST_USERNAME_STANDARD_1)))
                .andExpect(status().is(HttpStatus.OK.value())).andReturn().getResponse();
    }


    private ApiKey getApiKeyFromRepo(String apiKeyReceivedPass) {
        Optional<ApiKey> optionalApiKey =
                apiRepo.findByIdentifier(apiKeyReceivedPass.substring(0, ApiKey.IDENTIFIER_LENGTH));
        assertThat(optionalApiKey).isPresent();
        ApiKey apiKey = optionalApiKey.get();
        assertThat(apiKey.getIdentifier()).isNotNull();
        return apiKey;
    }

}
