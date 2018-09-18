package io.keikai.tutorial.app;

import io.keikai.client.api.*;
import io.keikai.client.api.event.*;
import io.keikai.client.api.ui.UiActivity;
import io.keikai.tutorial.web.AppContextListener;

import java.io.*;

/**
 * implement the application logic
 */
public class MyWorkflow {
    private Spreadsheet spreadsheet;
    private String role;

    static private String SHEET_LOGIN = "login";
    static private String SHEET_LIST= "form list";

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

    private void addEventListeners() {
        spreadsheet.getWorksheet(SHEET_LOGIN).getButton("login")
            .addAction((ShapeMouseEvent event) -> {
            login(spreadsheet.getRange("D2").getValue().toString());
        });
        spreadsheet.getWorksheet(SHEET_LIST).getButton("edit")
            .addAction((ShapeMouseEvent event) -> {
                if (spreadsheet.getActiveCell().getValue().toString().endsWith(".xlsx")) {
                    importFormFile(AppContextListener.loadFormList().get(spreadsheet.getActiveCell().getRow() - 1));
                }
        });
    }

    private void importFormFile(File formFile) {
        try {
            spreadsheet.importAndReplace(formFile.getName(), formFile);
            spreadsheet.getWorksheet(0).getButton("submit")
                .addAction((ShapeMouseEvent event) -> {
                    submit();
                });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (AbortedException e) {
            e.printStackTrace();
        }
    }

    /**
     * submit a form to the next phase: approve by a supervisor
     */
    private void submit() {
        //TODO store the form in to a table
    }

    public String getJavaScriptURI(String elementId) {
        return spreadsheet.getURI(elementId);
    }

    public void init(String bookName, File xlsxFile) throws FileNotFoundException, AbortedException {
        spreadsheet.importAndReplace(bookName, xlsxFile);
        addEventListeners();
    }

    private void login(String role){
        this.role = role;
        if (role.equals("employee")){
            spreadsheet.getWorksheet(SHEET_LIST).setVisible(Worksheet.Visibility.Visible);
            spreadsheet.setActiveWorksheet(SHEET_LIST);
            spreadsheet.getWorksheet(SHEET_LOGIN).setVisible(Worksheet.Visibility.Hidden);
            int row = 1;
            for (File file : AppContextListener.loadFormList()){
                spreadsheet.getRange(row, 0).setValue(file.getName());
                row++;
            }
            RangeEventListener formSelectionListener = new RangeEventListener() {

                @Override
                public void onEvent(RangeEvent rangeEvent) throws Exception {
                    if (rangeEvent.getRow() >=0 && rangeEvent.getRow() < AppContextListener.loadFormList().size()) {
                        File form = AppContextListener.loadFormList().get(rangeEvent.getRow());
                        spreadsheet.importAndReplace(form.getName(), form);
                        spreadsheet.removeEventListener(Events.ON_CELL_CLICK, this);
                    }
                }
            };
            spreadsheet.addEventListener(Events.ON_CELL_CLICK, formSelectionListener);
        }else{

        }
    }
}