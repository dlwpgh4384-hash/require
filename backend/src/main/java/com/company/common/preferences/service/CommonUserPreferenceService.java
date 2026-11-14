package com.company.common.preferences.service;

import com.company.common.preferences.mapper.UserPreferenceMapper;
import com.company.common.preferences.mapper.UserPreferenceRow;
import com.company.common.preferences.dto.UserPreferenceInput;
import com.company.common.preferences.dto.UserPreferenceResponse;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommonUserPreferenceService {
    private final UserPreferenceMapper mapper;
    private final UserPreferenceRules rules;
    private final UserPreferencePeriodBasis periodBasis;

    public CommonUserPreferenceService(UserPreferenceMapper mapper, UserPreferenceRules rules,
        UserPreferencePeriodBasis periodBasis) {
        this.mapper = mapper;
        this.rules = rules;
        this.periodBasis = periodBasis;
    }

    public UserPreferenceResponse getCommonUserPreference(String type, String key) {
        rules.validateKey(type, key);
        var response = UserPreferenceResponse.from(type, key, mapper.selectPreference(type, key));
        return type.equals("PERIOD")
            ? new UserPreferenceResponse(true, periodBasis.metadata(), response.data()) : response;
    }

    @Transactional
    public UserPreferenceResponse putCommonUserPreference(String type, String key, UserPreferenceInput input) {
        rules.validate(type, key, input.prefVal());
        UserPreferenceRow current = mapper.selectPreference(type, key);
        if (current == null) {
            if (input.modDttm() != null) throw UserPreferenceFailure.conflict();
            try {
                mapper.insertPreference(type, key, input.prefVal());
            } catch (DuplicateKeyException failure) {
                throw UserPreferenceFailure.conflict();
            }
        } else if (mapper.updatePreference(type, key, input.prefVal(), input.modDttm()) != 1) {
            throw UserPreferenceFailure.conflict();
        }
        return UserPreferenceResponse.from(type, key, mapper.selectPreference(type, key));
    }
}
