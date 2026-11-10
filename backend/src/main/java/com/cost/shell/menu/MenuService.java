package com.cost.shell.menu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {
    private static final Logger LOG = LoggerFactory.getLogger(MenuService.class);
    private final MenuMapper mapper;

    public MenuService(MenuMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public MenuGraph.Result list() {
        return MenuGraph.evaluate(mapper.findAll(), (kind, menuId) ->
            LOG.warn("{} menuId={}", kind, menuId.replaceAll("[\\r\\n\\t]", "_"))
        );
    }
}
