package io.keikai.tutorial.app;

import io.keikai.client.api.*;
import io.keikai.client.api.ctrl.Button;
import io.keikai.client.api.event.*;
import io.keikai.client.api.ui.*;
import io.keikai.tutorial.persistence.*;
import io.keikai.tutorial.web.AppContextListener;
import io.keikai.util.DateUtil;
import org.slf4j.*;

import java.io.*;
import java.time.*;
import java.util.*;

/**
 * implement the workflow logic
 *
 * @author Hawk Chen
 */
public class MyWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(MyWorkflow.class);

    static private final String ROLE_EMPLOYEE = "Employee";

    static private final String BUTTON_SUBMIT = "submit";
    static private final String BUTTON_CANCEL = "cancel";
    static private final String BUTTON_APPROVE = "approve";
    static private final String BUTTON_REJECT = "reject";

    static private String SHEET_MAIN = "main";
    static private String SHEET_FORM = "form list";
    static private String SHEET_SUBMISSION = "submission list";

    static private final int STARTING_COLUMN = 2;
    static private final int STARTING_ROW = 5;
    static private final String ROLE_CELL = "E6";

    private Spreadsheet spreadsheet;
    private String role;
    private String entryBookName;
    private File entryFile;
    private Submission submissionToReview = null;


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
            logger.error("Oops! something wrong in Spreadsheet", throwable);
        });
    }

    public String getJavaScriptURI(String elementId) {
        return spreadsheet.getURI(elementId);
    }

    public void init(String bookName, File xlsxFile) {
        this.entryBookName = bookName;
        this.entryFile = xlsxFile;
        start();
        navigateTo(SHEET_MAIN);
    }

    /**
     * start this workflow
     */
    private void start() {
        try {
            spreadsheet.clearEventListeners();
            spreadsheet.importAndReplace(this.entryBookName, this.entryFile);
            addEnterLeaveListeners();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (AbortedException e) {
            e.printStackTrace();
        }
    }

    private void addEnterLeaveListeners() {
        spreadsheet.getWorksheet(SHEET_MAIN).getButton("enter").addAction((ShapeMouseEvent) -> {
            this.role = spreadsheet.getRange(ROLE_CELL).getValue().toString();
            navigateByRole();
            showList();
        });
        spreadsheet.getWorksheet(SHEET_FORM).getButton("leave").addAction((ShapeMouseEvent) -> {
            leave();
        });
        spreadsheet.getWorksheet(SHEET_SUBMISSION).getButton("leave").addAction((ShapeMouseEvent) -> {
            leave();
        });
    }

    private void showForm(File formFile) throws FileNotFoundException, AbortedException {
        spreadsheet.clearEventListeners();
        spreadsheet.importAndReplace(formFile.getName(), formFile);
        setupButtonsUponRole(spreadsheet.getWorksheet());
    }


    private void showForm(Submission s) throws AbortedException {
        if (s.getState() == Submission.State.WAITING) {
            spreadsheet.clearEventListeners();
            spreadsheet.importAndReplace(s.getFormName(), new ByteArrayInputStream(s.getForm().toByteArray()));
            setupButtonsUponRole(spreadsheet.getWorksheet());
        }
    }

    private void setupButtonsUponRole(Worksheet worksheet) {
        Button submit = worksheet.getButton(BUTTON_SUBMIT);
        Button cancel = worksheet.getButton(BUTTON_CANCEL);
        Button approve = worksheet.getButton(BUTTON_APPROVE);
        Button reject = worksheet.getButton(BUTTON_REJECT);
        if (role.equals(ROLE_EMPLOYEE)) {
            submit.setVisible(true);
            cancel.setVisible(true);
            approve.setVisible(false);
            reject.setVisible(false);
            cancel.addAction(buttonShapeMouseEvent -> {
                navigateTo(SHEET_FORM);
            });
            submit.addAction((ShapeMouseEvent<Button> event) -> {
                submit();
                navigateTo(SHEET_FORM);
            });
        } else {
            submit.setVisible(false);
            cancel.setVisible(false);
            approve.setVisible(true);
            reject.setVisible(true);
            approve.addAction(shapeMouseEvent -> {
                approve();
                navigateTo(SHEET_SUBMISSION);
            });
            reject.addAction(buttonShapeMouseEvent -> {
                reject();
                navigateTo(SHEET_SUBMISSION);
            });
        }
    }

    private void reject() {
        submissionToReview.setLastUpdate(LocalDateTime.now());
        submissionToReview.setState(Submission.State.REJECTED);
        WorkflowDao.update(submissionToReview);
        submissionToReview = null;
    }

    private void approve() {
        submissionToReview.setLastUpdate(LocalDateTime.now());
        submissionToReview.setState(Submission.State.APPROVED);
        WorkflowDao.update(submissionToReview);
        submissionToReview = null;
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

    /**
     * show form or submission list according to the role
     */
    private void showList() {
        Worksheet sheet = spreadsheet.getWorksheet();
        if (role.equals(ROLE_EMPLOYEE)) {
            if (sheet.isProtected()) {
                sheet.unprotect("");
            }
            showFormList();
            addFormSelectionListener();
            sheet.protect("", false, false, false, false, false, false, false, false, false, false, false, false, false, false, true, true);
        } else { //supervisor
            if (sheet.isProtected()) {
                sheet.unprotect("");
            }
            showSubmissionList();
            //allow filter and sorting
            sheet.protect("", false, false, false, false, false, false, false, false, false, false, false, true, true, false, true, true);
        }
    }

    private void leave() {
        role = null;
        navigateTo(SHEET_MAIN);
    }

    private void addFormSelectionListener() {
        RangeEventListener formSelectionListener = new RangeEventListener() {

            @Override
            public void onEvent(RangeEvent rangeEvent) throws Exception {
                if (spreadsheet.getWorksheet().getName().equals(SHEET_FORM)
                        && rangeEvent.getRange().getValue().toString().endsWith(".xlsx")) {
                    File form = AppContextListener.getFormList().get(rangeEvent.getRow() - STARTING_ROW);
                    showForm(form);
                    spreadsheet.removeEventListener(Events.ON_CELL_CLICK, this);
                }
            }
        };
        spreadsheet.addEventListener(Events.ON_CELL_CLICK, formSelectionListener);
    }

    private void showFormList() {
        int row = STARTING_ROW;
        for (File file : AppContextListener.getFormList()) {
            spreadsheet.getRange(row, STARTING_COLUMN).setValue(file.getName());
            row++;
        }
    }

    private void showSubmissionList() {
        List<Submission> submissionList = WorkflowDao.queryAll();
        int row = STARTING_ROW;
        for (Submission s : submissionList) {
            spreadsheet.getRange(row, STARTING_COLUMN).setValue(s.getId());
            spreadsheet.getRange(row, STARTING_COLUMN + 1).setValue(s.getFormName());
            spreadsheet.getRange(row, STARTING_COLUMN + 2).setValue(s.getOwner());
            spreadsheet.getRange(row, STARTING_COLUMN + 3).setValue(s.getState());
            spreadsheet.getRange(row, STARTING_COLUMN + 4).setValue(DateUtil.getExcelDate(Date.from(s.getLastUpdate().atZone(ZoneId.systemDefault()).toInstant())));
            row++;
        }
        RangeEventListener submissionSelectionListener = new RangeEventListener() {

            @Override
            public void onEvent(RangeEvent rangeEvent) throws Exception {
                if (!rangeEvent.getWorksheet().getName().equals(SHEET_SUBMISSION)) {
                    return;
                }
                Range idCell = spreadsheet.getRange(rangeEvent.getRange().getRow(), STARTING_COLUMN);
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

    private void navigateByRole() {
        if (role.equals(ROLE_EMPLOYEE)) {
            spreadsheet.setActiveWorksheet(SHEET_FORM);
        }else{
            spreadsheet.setActiveWorksheet(SHEET_SUBMISSION);
        }
    }

    /**
     * show the target sheet, if it's necessary, import the entry file.
     */
    private Worksheet navigateTo(String sheetName) {
        if (spreadsheet.getBookName().equals(entryBookName)) {
            Worksheet currentSheet = spreadsheet.getWorksheet();
            if (!currentSheet.getName().equals(sheetName)) {
                spreadsheet.setActiveWorksheet(sheetName);
            }
        } else {
            start();
            navigateByRole();
            showList();
        }
        return spreadsheet.getWorksheet();

    }
}