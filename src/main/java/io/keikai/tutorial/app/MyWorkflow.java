package io.keikai.tutorial.app;

import io.keikai.client.api.*;
import io.keikai.client.api.ctrl.Button;
import io.keikai.client.api.event.*;
import io.keikai.client.api.ui.UiActivity;
import io.keikai.tutorial.persistence.*;
import io.keikai.tutorial.web.AppContextListener;
import io.keikai.util.DateUtil;

import java.io.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
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
    private Submission submissionToReview = null;

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
            String errorMessage = "Oops! " + throwable.getMessage();
            System.out.print(errorMessage);
            spreadsheet.getRange("A1").setValue(errorMessage);
            throwable.printStackTrace();
        });
    }

    private void addLoginLogoutListeners() {
        spreadsheet.getWorksheet(SHEET_LOGIN).getButton("login").addAction((ShapeMouseEvent event) -> {
            login(spreadsheet.getRange("B4").getValue().toString());
        });
        spreadsheet.getWorksheet(SHEET_FORM).getButton("logout").addAction((ShapeMouseEvent) -> {
            navigateToLoginPage();
        });
        spreadsheet.getWorksheet(SHEET_SUBMISSION).getButton("logout").addAction((ShapeMouseEvent) -> {
            navigateToLoginPage();
        });
    }

    private void showForm(File formFile) throws FileNotFoundException, AbortedException {
        spreadsheet.importAndReplace(formFile.getName(), formFile);
        setupButtonsUponRole(spreadsheet.getWorksheet());
    }


    private void showForm(Submission s) throws AbortedException {
        if (s.getState() == Submission.State.WAITING) {
            spreadsheet.importAndReplace(s.getFormName(), new ByteArrayInputStream(s.getForm().toByteArray()));
            setupButtonsUponRole(spreadsheet.getWorksheet());
        }
    }

    private void setupButtonsUponRole(Worksheet worksheet) {
        Button submit = worksheet.getButton(BUTTON_SUBMIT);
        Button approve = worksheet.getButton(BUTTON_APPROVE);
        Button reject = worksheet.getButton(BUTTON_REJECT);
        if (role.equals(ROLE_EMPLOYEE)) {
            submit.setVisible(true);
            approve.setVisible(false);
            reject.setVisible(false);
            submit.addAction((ShapeMouseEvent event) -> {
                submit();
                navigateTo(SHEET_FORM);
            });
        } else {
            submit.setVisible(false);
            approve.setVisible(true);
            reject.setVisible(true);
            approve.addAction(shapeMouseEvent -> {
                approve();
                navigateTo(SHEET_SUBMISSION);
            });
        }
    }

    private void approve() {
        submissionToReview.setLastUpdate(LocalDateTime.now());
        submissionToReview.setState(Submission.State.APPROVED);
        WorkflowDao.update(submissionToReview);
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
        submission.setOwner(this.role);
        WorkflowDao.insert(submission);
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
    }

    private void addFormSelectionListener() {
        RangeEventListener formSelectionListener = new RangeEventListener() {

            @Override
            public void onEvent(RangeEvent rangeEvent) throws Exception {
                if (spreadsheet.getWorksheet().getName().equals(SHEET_FORM)
                        && rangeEvent.getRange().getValue().toString().endsWith(".xlsx")) {
                    File form = AppContextListener.getFormList().get(rangeEvent.getRow() - 2);
                    showForm(form);
                    spreadsheet.removeEventListener(Events.ON_CELL_CLICK, this);
                }
            }
        };
        spreadsheet.addEventListener(Events.ON_CELL_CLICK, formSelectionListener);
    }

    private void showFormList() {
        int row = 2;
        for (File file : AppContextListener.getFormList()) {
            spreadsheet.getRange(row, 0).setValue(file.getName());
            row++;
        }
    }

    /**
     * show waiting submissions
     */
    private void listSubmission() {
        List<Submission> submissionList = WorkflowDao.queryAll();
        int row = 3;
        for (Submission s : submissionList) {
            spreadsheet.getRange(row, 0).setValue(s.getId());
            spreadsheet.getRange(row, 1).setValue(s.getFormName());
            spreadsheet.getRange(row, 2).setValue(s.getOwner());
            spreadsheet.getRange(row, 3).setValue(s.getState());
            spreadsheet.getRange(row, 4).setValue(DateUtil.getExcelDate(Date.from(s.getLastUpdate().atZone(ZoneId.systemDefault()).toInstant())));
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
                        submissionToReview = s;
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
        if (!spreadsheet.getWorksheet().getName().equals(targetSheetName)) {
            spreadsheet.getWorksheet(targetSheetName).setVisible(Worksheet.Visibility.Visible);
            spreadsheet.getWorksheet().setVisible(Worksheet.Visibility.Hidden);
            spreadsheet.setActiveWorksheet(targetSheetName);
        }
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

    private void navigateTo(String sheetName) {
        if (spreadsheet.getBookName().equals(entryBookName)) {
            navigateToSheet(sheetName);
        } else {
            try {
                spreadsheet.importAndReplace(entryBookName, entryFile);
                login(this.role);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (AbortedException e) {
                e.printStackTrace();
            }
        }

    }
}