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
    public static final String BUTTON_SUBMIT = "submit";
    private static final String BUTTON_APPROVE = "approve";
    private static final String BUTTON_REJECT = "reject";
    private Spreadsheet spreadsheet;
    private String role;
    private String entryBookName;
    private File entryFile;
    private ByteArrayInputStream entryBookInputStream;

    static private String SHEET_LOGIN = "login";
    static private String SHEET_FORM = "form list";
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
        spreadsheet.addExceptionHandler(throwable -> {
            System.out.print("Oops! "+ throwable.getMessage());
            throwable.printStackTrace();
        });
    }

    private void addLoginLogoutListeners() {
        spreadsheet.getWorksheet(SHEET_LOGIN).getButton("login")
                .addAction((ShapeMouseEvent event) -> {
                    login(spreadsheet.getRange("D2").getValue().toString());
                });
        spreadsheet.getWorksheet(SHEET_FORM).getButton("logout").addAction((ShapeMouseEvent) -> {
            navigateToLoginPage();
        });
        spreadsheet.getWorksheet(SHEET_SUBMISSION).getButton("logout").addAction((ShapeMouseEvent) -> {
            navigateToLoginPage();
        });
    }

    private void showForm(File formFile) {
        try {
            spreadsheet.importAndReplace(formFile.getName(), formFile);
            setupButtonsUponRole(spreadsheet.getWorksheet());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (AbortedException e) {
            e.printStackTrace();
        }
    }


    private void showForm(Submission s) throws AbortedException {
        spreadsheet.importAndReplace(s.getFormName(), new ByteArrayInputStream(s.getForm().toByteArray()));
        setupButtonsUponRole(spreadsheet.getWorksheet());
    }

    private void setupButtonsUponRole(Worksheet worksheet) {
        if (role.equals(ROLE_EMPLOYEE)) {
            worksheet.getButton(BUTTON_SUBMIT).setVisible(true);
            worksheet.getButton(BUTTON_APPROVE).setVisible(false);
            worksheet.getButton(BUTTON_REJECT).setVisible(false);
            worksheet.getButton(BUTTON_SUBMIT)
                    .addAction((ShapeMouseEvent event) -> {
                        submit();
                    });
        } else {
            worksheet.getButton(BUTTON_SUBMIT).setVisible(false);
            worksheet.getButton(BUTTON_APPROVE).setVisible(true);
            worksheet.getButton(BUTTON_REJECT).setVisible(true);
            worksheet.getButton(BUTTON_APPROVE).addAction(shapeMouseEvent -> {
                navigateToMain();
            });
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
        navigateToMain();
    }

    private void navigateToMain() {
        try {
            spreadsheet.importAndReplace(entryBookName, entryBookInputStream);
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
        addLoginLogoutListeners();
    }

    private void login(String role) {
        this.role = role;
        if (role.equals(ROLE_EMPLOYEE)) {
            navigateToSheet(SHEET_FORM);
            showFormList();
            addFormSelectionListener();
        } else { //supervisor
            navigateToSheet(SHEET_SUBMISSION);
            listSubmission();
        }
        cacheBookState();
    }

    private void addFormSelectionListener() {
        RangeEventListener formSelectionListener = new RangeEventListener() {

            @Override
            public void onEvent(RangeEvent rangeEvent) throws Exception {
                if (spreadsheet.getWorksheet().getName().equals(SHEET_FORM)
                        && rangeEvent.getRange().getValue().toString().endsWith(".xlsx")) {
                    File form = AppContextListener.getFormList().get(rangeEvent.getRow() - 1);
                    showForm(form);
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
                if (!rangeEvent.getWorksheet().getName().equals(SHEET_SUBMISSION)) {
                    return;
                }
                Range idCell = spreadsheet.getRange(rangeEvent.getRange().getRow(), 0);
                int id = idCell.getRangeValue().getCellValue().getDoubleValue().intValue();
                for (Submission s : submissionList) {
                    if (s.getId() == id) {
                        showForm(s);
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
            if (!spreadsheet.getBookName().equals(entryBookName)) {
                spreadsheet.importAndReplace(entryBookName, entryBookInputStream);
                addLoginLogoutListeners();
            }
            navigateToSheet(SHEET_LOGIN);
        } catch (AbortedException e) {
            e.printStackTrace();
        }
    }

    private void navigateTo(String bookName, String sheetName){

    }
}