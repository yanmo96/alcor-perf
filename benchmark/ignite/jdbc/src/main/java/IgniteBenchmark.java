/*
MIT License
Copyright(c) 2020 Futurewei Cloud

    Permission is hereby granted,
    free of charge, to any person obtaining a copy of this software and associated documentation files(the "Software"), to deal in the Software without restriction,
    including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and / or sell copies of the Software, and to permit persons
    to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
    WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

import java.io.FileInputStream;
import java.io.FileReader;
import java.lang.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;
// import Adgen.*;

// import org.apache.ignite.examples.ExampleNodeStartup;

/**
 * This example demonstrates usage of Ignite JDBC driver.
 * <p>
 * Ignite nodes must be started in separate process using ExampleNodeStartup before running this example.
 */
public class IgniteBenchmark {
    public static Adgen.CommandLine cmdLine = new Adgen.CommandLine();

    public static class DataBuffer
    {
        public DataBuffer()
        {}

        public void addRow(ArrayList<String> row)
        {
            dataBuffer.add(row);
        }
        public ArrayList<ArrayList<String>> dataBuffer = new ArrayList<ArrayList<String>>();
    }

    public static String /*List<String>*/ readSchemaFile()
    {
        /*
        String tblDrp = "drop table if exists nodeinfo;";
        String tblCrt = "create table nodeinfo\n" +
                "(\n" +
                "        node_id         VARCHAR PRIMARY KEY,\n" +
                "        node_name       VARCHAR,\n" +
                "        loacl_ip        VARCHAR(40),\n" +
                "        mac_address     VARCHAR(40),\n" +
                "        veth            int,\n" +
                "        host_dvr_mac    VARCHAR(18)\n" +
                ") WITH \"template=replicated\";";

        ArrayList<String> strings = new ArrayList<String>();
        strings.add(tblDrp);
        strings.add(tblCrt);
        return strings;
        */
        String tblDef = null;
        try {
            tblDef = new String(Files.readAllBytes(Paths.get(cmdLine.inputFileName)));
            return tblDef;
        }
        catch (Exception e) {
            System.out.println(e);
            System.exit(-1);
        }

        return tblDef;
    }

    public static String genInsertStmt(Adgen.TableDef tblDef)
    {
        StringBuilder stmt = new StringBuilder("INSERT INTO ");
        stmt.append(tblDef.tblName);
        stmt.append(" (");
        for (int i = 0; i < tblDef.columns.size(); ++i) {
            stmt.append(tblDef.columns.get(i).name);
            if (i < tblDef.columns.size() - 1)
                stmt.append(", ");
        }

        stmt.append(") VALUES (");
        for (int i = 0; i < tblDef.columns.size(); ++i) {
            stmt.append("?");
            if (i < tblDef.columns.size() - 1)
                stmt.append(", ");
        }
        stmt.append(");");

        return stmt.toString();
    }

    public static void bindRow(PreparedStatement insStmt, DataBuffer dataBuffer, Adgen.TableDef tblDef, int r)
    {
        ArrayList<String> row = dataBuffer.dataBuffer.get(r);
        try {
            for (int i = 0; i < row.size(); ++i) {
                int p = i + 1;
                switch (tblDef.columns.get(i).type) {
                    case AT_IP:
                    case AT_IPV4:
                    case AT_IPV6:
                    case AT_MAC:
                    case AT_STRING:
                        insStmt.setString(p, row.get(i));
                        break;
                    case AT_INT:
                        insStmt.setInt(p, Integer.valueOf(row.get(i)));
                        break;
                    default:
                        System.out.println("Unexpected type");
                        System.exit(-1);
                }
            }
        }
        catch (Exception e) {
            System.out.println(e);
            System.exit(-1);
        }
    }

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     * @throws Exception If example execution failed.
     */
    public static void main(String[] args) throws Exception
    {
        print("JDBC example started.");

        Adgen.processArgs(args, cmdLine);

        DriverManager.getDrivers();
        Class.forName("org.apache.ignite.IgniteJdbcThinDriver");

        // Open JDBC connection
        Connection conn = null;

        try {
            conn = DriverManager.getConnection("jdbc:ignite:thin://127.0.0.1/");
            print("Connected to server.");

            // Create database objects.
            Statement stmt = conn.createStatement();
            String ddlStmts = readSchemaFile();

            stmt.executeUpdate(ddlStmts);

            print("Created database objects.");

            DataBuffer dataBuffer = new DataBuffer();
            Adgen.TableDef tblDef = Adgen.getTableDef(cmdLine.inputFileName);
            Adgen.genData(tblDef, dataBuffer, cmdLine.numRows);
            String insStr = genInsertStmt(tblDef);
            PreparedStatement insStmt = null;
            insStmt = conn.prepareStatement(insStr);

            long startTime = System.nanoTime();
            for (int r = 0; r < cmdLine.numRows; ++r) {
                bindRow(insStmt, dataBuffer, tblDef, r);
                insStmt.executeUpdate();
            }
            stmt.execute("commit;");
            long stopTime = System.nanoTime();
            print("Populated data: " + cmdLine.numRows + " in " + (stopTime - startTime) + " ns");

            print("Running query");
            // Get data.
            String qrySql = "select * from " + tblDef.tblName + ";";
            startTime = System.nanoTime();
            ResultSet rs = stmt.executeQuery(qrySql);
            print("Query results:");
            int resCount = 0;

            while (rs.next()) {
               ++resCount;
            }
            stopTime = System.nanoTime();
            print("Retrieved " + resCount + " rows in " + (stopTime - startTime) + " ns");
        }
        catch (Exception e) {
            System.out.println(e);
            System.exit(-1);
        }

        print("JDBC example finished.");
    }

    /**
     * Prints message.
     *
     * @param msg Message to print before all objects are printed.
     */
    private static void print(String msg) {
        System.out.println();
        System.out.println(">>> " + msg);
    }
}
