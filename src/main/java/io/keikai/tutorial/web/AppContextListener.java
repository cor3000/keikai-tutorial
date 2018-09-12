package io.keikai.tutorial.web;

import io.keikai.tutorial.persistence.*;

import javax.servlet.*;
import javax.servlet.annotation.WebListener;
import java.io.InputStream;
import java.util.*;

@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        SampleDataDao.initDatabase();
        WorkflowDao.initDatabase(getFormList(servletContextEvent.getServletContext()));
    }

    private List<InputStream> getFormList(ServletContext context) {
        List<InputStream> list = new LinkedList<>();
        list.add(context.getResourceAsStream("/WEB-INF/form_leave.xlsx"));
        return list;
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}