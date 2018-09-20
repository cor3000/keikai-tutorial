package io.keikai.tutorial.app;

import io.keikai.client.api.*;
import io.keikai.client.api.event.*;
import io.keikai.client.api.ui.UiActivity;
import io.keikai.tutorial.persistence.*;
import io.keikai.tutorial.web.AppContextListener;

import java.io.*;
import java.util.*;

/**
 * implement the application logic
 */
public class MyWorkflow {
    public static final String ROLE_EMPLOYEE = "employee";
    private Spreadsheet spreadsheet;
    private String role;
    private String entryBookName;
    private File entryFile;
    private ByteArrayInputStream entryBookInputStream;

    static private String SHEET_LOGIN = "login";
    static private String SHEET_LIST = "form list";
    static private String SHEET_SUBMISSION = "submission list";

    public MyWorkflow(String keikaiServerAddress) {
        spreadsheet = Keikai.newClient(keikaiServerAddress);
        // close spreadsheet Java client when a browser disconnects to keikai server to avoid memory leak
        spreadsheet.setUiActivityCallback(new UiActivity() {
            public void onConnect() {
            }

            public void onDisconnect() {
                spreadsheet.close();
            }
        });
    }

    private void addLoginListeners() {
        spreadsheet.getWorksheet(SHEET_LOGIN).getButton("login")
                .addAction((ShapeMouseEvent event) -> {
                    login(spreadsheet.getRange("D2").getValue().toString());
                });
    }

    private void importFormFile(File formFile) {
        try {
            spreadsheet.importAndReplace(formFile.getName(), formFile);
            spreadsheet.getWorksheet().getButton("submit")
                    .addAction((ShapeMouseEvent event) -> {
                        submit();
                    });
            showButtonsUponRole();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (AbortedException e) {
            e.printStackTrace();
        }
    }

    private void showButtonsUponRole() {
        if (role.endsWith(ROLE_EMPLOYEE)) {
            //TODO show submit only
        } else {
            //TODO show approve, reject
        }
    }

    /**
     * submit a form to the next phase: approve by a supervisor
     */
    private void submit() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        spreadsheet.export(spreadsheet.getBookName(), outputStream);
        Submission submission = new Submission();
        submission.setForm(outputStream);
        submission.setFormName(spreadsheet.getBookName());
        WorkflowDao.insert(submission);
        try {
            spreadsheet.importAndReplace(entryBookName, entryBookInputStream);
            navigateToSheet(SHEET_LIST);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getJavaScriptURI(String elementId) {
        return spreadsheet.getURI(elementId);
    }

    public void init(String bookName, File xlsxFile) throws FileNotFoundException, AbortedException {
        this.entryBookName = bookName;
        this.entryFile = xlsxFile;
        spreadsheet.importAndReplace(bookName, xlsxFile);
        addLoginListeners();
    }

    private void login(String role) {
        this.role = role;
        if (role.equals(ROLE_EMPLOYEE)) {
            navigateToSheet(SHEET_LIST);
            showFormList();
            addFormSelectionListener();
        } else { //supervisor
            navigateToSheet(SHEET_SUBMISSION);
            listSubmission();
        }
        cacheBookState();
        addLogoutListner();
    }

    private void addLogoutListner() {
        spreadsheet.getWorksheet().getButton("logout").addAction((ShapeMouseEvent) -> {
            navigateToLoginPage();
        });
    }

    private void addFormSelectionListener() {
        RangeEventListener formSelectionListener = new RangeEventListener() {

            @Override
            public void onEvent(RangeEvent rangeEvent) throws Exception {
                if (rangeEvent.getRange().getValue().toString().endsWith(".xlsx")) {
                    File form = AppContextListener.getFormList().get(rangeEvent.getRow() - 1);
                    importFormFile(form);
                    spreadsheet.removeEventListener(Events.ON_CELL_CLICK, this);
                }
            }
        };
        spreadsheet.addEventListener(Events.ON_CELL_CLICK, formSelectionListener);
    }

    private void showFormList() {
        int row = 1;
        for (File file : AppContextListener.getFormList()) {
            spreadsheet.getRange(row, 0).setValue(file.getName());
            row++;
        }
    }

    private void cacheBookState() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        spreadsheet.export(spreadsheet.getBookName(), outputStream);
        this.entryBookInputStream = new ByteArrayInputStream(outputStream.toByteArray());
    }

    /**
     * show waiting submissions
     */
    private void listSubmission() {
        List<Submission> submissionList = WorkflowDao.queryAll();
        for (Iterator<Submission> iterator = submissionList.iterator(); iterator.hasNext(); ) {
            Submission s = iterator.next();
            if (s.getState() != Submission.State.WAITING) {
                iterator.remove();
            }
        }
        int row = 1;
        for (Submission s : submissionList) {
            spreadsheet.getRange(row, 0).setValue(s.getId());
            spreadsheet.getRange(row, 1).setValue(s.getFormName());
            spreadsheet.getRange(row, 2).setValue(s.getLastUpdate().toString());
            row++;
        }
        RangeEventListener submissionSelectionListener = new RangeEventListener() {

            @Override
            public void onEvent(RangeEvent rangeEvent) throws Exception {
                int id = spreadsheet.getRange(rangeEvent.getRange().getRow(), 0).getRangeValue().getCellValue().getDoubleValue().intValue();
                for (Submission s : submissionList) {
                    if (s.getId() == id) {
                        spreadsheet.importAndReplace(s.getFormName(), new ByteArrayInputStream(s.getForm().toByteArray()));
                        break;
                    }
                }
            }
        };
        spreadsheet.addEventListener(Events.ON_CELL_CLICK, submissionSelectionListener);
    }

    /**
     * show the target sheet and hide others
     *
     * @param targetSheetName
     */
    private void navigateToSheet(String targetSheetName) {
        spreadsheet.getWorksheet(targetSheetName).setVisible(Worksheet.Visibility.Visible);
        spreadsheet.getWorksheet().setVisible(Worksheet.Visibility.Hidden);
        spreadsheet.setActiveWorksheet(targetSheetName);
    }

    private void navigateToLoginPage() {
        try {
            spreadsheet.importAndReplace(entryBookName, entryBookInputStream);
            navigateToSheet(SHEET_LOGIN);
        } catch (AbortedException e) {
            e.printStackTrace();
        }
    }
}