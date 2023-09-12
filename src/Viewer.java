
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Viewer {

    private static final int ARROW_UP = 1000, ARROW_DOWN = 1001, ARROW_RIGHT = 1002, ARROW_LEFT = 1003
            , PG_UP = 1004, PG_DOWN = 1005, HOME = 1006, END = 1007, DEL = 1008, BACKSPACE = 127;/**ASCII codes of the mentioned keys*/

    private static int rows = 10;
    private static int columns = 10;

    private static int cursorX = 0, offsetX = 0, cursorY = 0, offsetY = 0;/**location of our vusible cursor to write*/

    private static Terminal terminal =
            Platform.isWindows() ? new WindowsTerminal() :
                    Platform.isMac() ? new MacOsTerminal() : new UnixTerminal();/**terminal instance, made compatible with the 3 main OS*/
    private static List<String> content = new ArrayList<>();/**conotent of the file specified*/
    static String statusMessage;/**the message at the bottom of the screen*/
    private static Path currentFile;

    private static String[] fileToOpen;

    public static void main(String[] args) throws IOException {
//
        menuLoop();

    }

    private static void menuLoop() throws IOException {
        help();
        cursorX = 0;
        cursorY = 0;
        System.out.print("\033[H");

        while(true){
            initEditor();

            while(true)
            {
                refreshScreen();
                int key = readKey();
                handleKey(key);
            }
        }
    }


public static void ValidateJsonFile(String path){
    /**Toolkit.getDefaultToolkit().beep();*/
    try {
        ObjectMapper objectMapper = new ObjectMapper();
        File jsonFile = new File(path);

        /** Attempt to parse the JSON file*/
        objectMapper.readTree(jsonFile);
        setStatusMessage("JSON syntax is valid.");

    } catch (JsonParseException e) {
        setStatusMessage("JSON syntax is invalid: " + e.getMessage());
    } catch (IOException e) {
        setStatusMessage("An error occurred while reading the file");
    }

}




    private static void help() throws IOException {
        System.out.print("\033[H");

        System.out.println(">");
        System.out.println("JSON editor 3.1");
        System.out.println("To insert a command type '>' + 'command' + 'enter' on a new line:");
        System.out.println(">help       = list of commands          = ctrl + l");
        System.out.println(">find       = search the file           = ctrl + f");
        System.out.println(">save       = save opened file          = ctrl + s");
        System.out.println(">saveas     = save file at location     = ctrl + e");
        System.out.println(">open       = open file with path       = ctrl + o");
        System.out.println(">close      = close opened file         = ctrl + c");
        System.out.println(">validate   = validate if JSON          = ctrl + v");
        System.out.println(">quit       = exit JSON editor          = ctrl + q");
        System.out.println(">");
        System.out.println(">");
        System.out.println("ctrl + 'r'  = set default status message          ");
        System.out.println(">");
        System.out.println(">");
        System.out.println("ARROWS/HOME/END/PGUP/PGDN/ENTER         to navigate");
        System.out.println("BACKSPACE/DELETE/ctrl + h               to delete");
        System.out.println(">");
        System.out.println(">");
        System.out.print("Press enter to continue...");


        int key = System.in.read();
        if (key == '\r'){return;}

    }

    private static void scroll() {
        if(cursorY >= rows + offsetY){
            offsetY = cursorY - rows + 1;
        }
        else if (cursorY < offsetY){
            offsetY = cursorY;
        }

        if(cursorX >= columns + offsetX){
            offsetX = cursorX - columns + 1;
        }
        else if (cursorX < offsetX){
            offsetX = cursorX;
        }
    }

    private static void openFile(String args) {
        //Toolkit.getDefaultToolkit().beep();
        if(args != null && !args.isEmpty()){
            Path path = Path.of(args);
            if(Files.exists(path)){
                try (Stream<String> stream = Files.lines(path)){
                    content = stream.collect(Collectors.toCollection(ArrayList::new));
                }catch (IOException e){
                    // TODO display message in status bar
                    //throw new RuntimeException(e);
                }
            }
            currentFile = path;

        }
        setStatusMessage("File opened successfully");
    }

    private static void initEditor() {
        terminal.enableRawMode();/**raw mode is the terminal that does not recognize commands*/
        WindowSize windowSize = terminal.getWindowSize();
        columns = windowSize.columns();
        rows = windowSize.rows() - 1;
    }

    private static void refreshScreen()
    {
        scroll();/**scrolls screen when cursor is about to get out of visibility*/
        StringBuilder builder = new StringBuilder();/**strigBuilder used for all the content on the display*/
        moveCursorToTopLeft(builder);
        drawContent(builder);/**draws the content of the file, if none draws '~'*/
        drawStatusBar(builder);
        drawCursor(builder);
        System.out.print(builder);
    }

    private static void moveCursorToTopLeft(StringBuilder builder) {
        builder.append("\033[H");/**escape sequence and moves cursor to top left*/
    }

    private static void drawCursor(StringBuilder builder) {
        builder.append(String.format("\033[%d;%dH", cursorY - offsetY + 1, cursorX - offsetX + 1));/**escape sequence and move cursor*/
    }

    private static void drawStatusBar(StringBuilder builder) {
        // old message: String statusMessage = "Chakarov Editor - v0.0.1";
        String message = statusMessage != null ? statusMessage : "Rows: " + rows + " X:" + cursorX + " Y:" + cursorY
                + " arrows/home/end/pgup/pgdn      " +
                " ctrl's'     ctrl'e'    ctrl'f'   ctrl'o'   ctrl'c'  ctrl'v'  ctrl'q'";
        builder.append("\033[7m")
                .append(message)
                .append( " ".repeat(Math.max(0, columns - message.length())))
                .append( "\033[0m");
    }
    public static void setStatusMessage(String statusMessage){
        Viewer.statusMessage = statusMessage;
    }

    private static void drawContent(StringBuilder builder) {
        for(int i = 0; i< rows; i++){
            int fileI = offsetY + i;

            if(fileI >= content.size()) {
                builder.append("~");
            }else{
                String line = content.get(fileI);
                int lengthToDraw = line.length() - offsetX;

                if (lengthToDraw < 0){
                    lengthToDraw = 0;
                }
                if(lengthToDraw > columns){
                    lengthToDraw = columns;
                }
                if(lengthToDraw > 0){
                    builder.append(line, offsetX, offsetX + lengthToDraw);
                }

            }
            builder.append("\033[K\r\n");
        }
    }

    private static int readKey() throws IOException{
        int key = System.in.read();
        if (key != '\033')
        {
            return key;/**checks for escape sequence*/
        }
        int nextKey = System.in.read();
        if(nextKey != '[' && nextKey != 'O'){
            return nextKey;/**checks for beginning of message after escape sequence, used for arrows*/
        }
        int yetAnotherKey = System.in.read();/**third key*/

        if(nextKey == '['){
            return switch (yetAnotherKey){
                case 'A' -> ARROW_UP; //e.g. esc[A == arrow_up
                case 'B' -> ARROW_DOWN;
                case 'C' -> ARROW_RIGHT;
                case 'D' -> ARROW_LEFT;
                case 'H' -> HOME;
                case 'F' -> END;
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> { // e.g. esc[5! == pg_up
                    int yetYetAnotherChar = System.in.read();
                    if(yetYetAnotherChar != '~'){
                        yield yetYetAnotherChar;
                    }
                    switch (yetAnotherKey){
                        case '1':
                        case '7':
                            yield HOME;
                        case '3':
                            yield DEL;
                        case '4':
                        case '8':
                            yield END;
                        case '5':
                            yield PG_UP;
                        case '6':
                            yield PG_DOWN;
                        default: yield yetAnotherKey;
                    }
                }
                default -> yetAnotherKey;
            };

        }else {//if (nextKey == ']') {e.g. escpOH == HOME
            return switch (yetAnotherKey){
                case 'H' -> HOME;
                case 'F' -> END;
                default -> yetAnotherKey;
            };
        }
    }

    private static void handleKey(int key){/**handles shortcuts*/

        if(key == ctrl_key('q'))
        {
            exit();
        }else if(key == ctrl_key('s')){/**ctrl_key is a method that does a bitwise operation to detect a ctrl key press*/
            editorSave();
        }else if(key == ctrl_key('e')){
            editorSaveAs();
        }else if(key == '\r') {
            handleEnter();
        }else if (key == ctrl_key('f')){
            editorFind();
        }
        else if (key == ctrl_key('r')){
            setStatusMessage(null);
        }else if (key == ctrl_key('o')){
            editorOpenFile();
        }else if(key == ctrl_key('v')) {
            ValidateJsonFile(String.valueOf(currentFile));
        }else if (key == ctrl_key('c')){
            closeOpenedFile();
            cursorX = 0;
            cursorY = 0;
            System.out.print("\033[H");
        }else if (key == ctrl_key('l')){
            try {
                help();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else if (List.of(DEL, BACKSPACE, ctrl_key('h')).contains(key)) {
            deleteChar();

        } else if (List.of(ARROW_UP, ARROW_LEFT, ARROW_RIGHT, ARROW_DOWN, HOME, END, PG_DOWN, PG_UP).contains(key)){
            moveCursor(key);
        }else {
            insertChar((char) key);
        }



       // scanner.close(); // Close the scanner when you're done with it
            /*else{
                System.out.print((char) + key + " -> (" + key + ")\r\n");
            }*/
    }

    private static void editorSaveAs() {/**handles the saveAs when called from the edtior*/
        StringBuilder userInput = new StringBuilder();
        String message = "Set path save as (Use ESC/Enter)";

        while(true)
        {
            try {
                setStatusMessage(!userInput.isEmpty() ? userInput.toString() : message);/**displays the userInput, if any, into the status bar*/
                refreshScreen();
                int key = readKey();

                if (key == '\033'){
                    setStatusMessage(null);
                    return;
                }else if (key == '\r') {
                    message = "File saved successfully";

                    saveAs(userInput);/**acrtual save funcion*/
                    return;
                } else if (key == DEL || key == BACKSPACE) {
                    if(!userInput.isEmpty()){
                        userInput.deleteCharAt(userInput.length() - 1);
                    }
                } else if (!Character.isISOControl(key) && key < 128) {
                    userInput.append((char) key);
                }


            }catch (IOException e){
                throw new RuntimeException(e);
            }
        }


    }

    private static void saveAs(StringBuilder userInput) {
        String location = String.valueOf(userInput);
        try {
            setStatusMessage("File saved successfully!");
            Files.write(Path.of(location), content);/**writes the content into the path specified*/

        } catch (IOException e) {
            setStatusMessage("There was a problem saving your file " + e.getMessage());
            e.printStackTrace();
        }

    }

    private static void closeOpenedFile() {/**clears all loaded data*/
        setStatusMessage("File closed successfully");
        Path path = Path.of("");
        content = new ArrayList<>();
        currentFile = null;
    }

    private static void editorOpenFile() {/**opens specified file, works similarly to the save file method*/

        StringBuilder userInput = new StringBuilder();
        String message = "Set path to open file (Use ESC/Enter)";

        while(true)
        {
            try {
                setStatusMessage(!userInput.isEmpty() ? userInput.toString() : message);
                refreshScreen();
                int key = readKey();

                if (key == '\033'){
                    setStatusMessage(null);
                    return;
                }else if (key == '\r') {
                    message = "File opened successfully";
                    openFile(String.valueOf(userInput));/**actual open file method*/
                    return;
                } else if (key == DEL || key == BACKSPACE) {
                    if(!userInput.isEmpty()){
                        userInput.deleteCharAt(userInput.length() - 1);
                    }
                } else if (!Character.isISOControl(key) && key < 128) {
                    userInput.append((char) key);
                }


            }catch (IOException e){
                throw new RuntimeException(e);
            }
        }


    }



    private static void editorSave() {/**handles save funcitionality, saves to the same path*/
        if(currentFile != null) {
            try {
                Files.write(currentFile, content);
                setStatusMessage("File saved successfully!");
            } catch (IOException e) {
                setStatusMessage("There was a problem saving your file " + e.getMessage());
                e.printStackTrace();
            }
        }else{
            setStatusMessage("No file opened! Nothing to save...");
            Thread messageThread = new Thread(() -> {
                try {
                    Thread.sleep(3000); // Sleep for 3 seconds
                } catch (InterruptedException e) {
                    // Handle the InterruptedException if necessary
                    throw new RuntimeException(e);
                } finally {
                    setStatusMessage(null); // Clear the message after 3 seconds
                }
            });

            messageThread.start(); // Start the thread
        }
    }

    private static void deleteChar() {/**delete method that is used for backspace */
        if(cursorX == 0 && cursorY == 0){
            return;
        }
        if(cursorY == content.size()){
            return;
        }

        if(cursorX > 0) {
            deleteCharacterFromRow(cursorY, cursorX - 1);
            cursorX--;
        }else{
            cursorX = content.get(cursorY - 1).length();
            appendStringToRow(cursorY - 1, content.get(cursorY));
            deleteRow(cursorY);
            cursorY--;
        }
    }

    private static void deleteRow(int at) {
        if(at < 0 || at >= content.size())return;
        content.remove(at);
    }

    private static void appendStringToRow(int at, String append) {
        content.set(at, content.get(at) + append);
    }


    private static void deleteCharacterFromRow(int row, int at) {
        String line = content.get(row);
        if(at < 0 || at > line.length()) return;
        String editedLine = new StringBuilder(line).deleteCharAt(at).toString();
        content.set(row, editedLine);
    }

    private static void handleEnter() {/**used not only for adding rows but also for handling the commands such as >help + \r*/
        //handle command functions:
        String lineCommand = content.get(cursorY);
        handleCommand(lineCommand);

        //regular Enter functions:
        if(cursorX == 0){
            insertRowAt(cursorY, "");
        }else {
            String line = content.get(cursorY);
            insertRowAt(cursorY + 1, line.substring(cursorX));
            content.set(cursorY, line.substring(0, cursorX));
        }
        cursorY++;
        cursorX = 0;
    }

    private static void handleCommand(String lineCommand) {
        if (Objects.equals(lineCommand, ">help")){
            try {
                help();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else if(Objects.equals(lineCommand, ">quit")){
            exit();
        }else if(Objects.equals(lineCommand, ">save")){
            editorSave();
        }else if(Objects.equals(lineCommand, ">validate")){
            ValidateJsonFile(String.valueOf(currentFile));
        }else if(Objects.equals(lineCommand, ">saveas")){
            editorSaveAs();
        }else if(Objects.equals(lineCommand, ">find")){
            editorFind();
        }else if(Objects.equals(lineCommand, ">close")){
            closeOpenedFile();
            System.out.print("\033[H");
        }else if(Objects.equals(lineCommand, ">open")){
            editorOpenFile();
        }
    }

    private static void insertChar(char key) {/**used when typing*/

        if(cursorY == content.size()){
            insertRowAt(cursorY, "");
        }
        insertCharInRow(cursorY, cursorX, key);
        cursorX++;
    }

    private static void insertRowAt(int at, String rowContent) {/**inserts row at a loaction*/
        if(at < 0 || at > content.size()) return;
        content.add(at, rowContent);
    }

    private static void insertCharInRow(int row, int at, char key) {
           String line = content.get(row);
           if(at < 0 || at > line.length()) at = line.length();
           String editedLine = new StringBuilder(line).insert(at, key).toString();
           content.set(row, editedLine);
    }

    enum SearchDirection {
        FORWARDS, BACKWARDS
    }
    static SearchDirection searchDirection = SearchDirection.FORWARDS;
    static int lastMatch = -1;

    private static void editorFind() {/**method for searching in file, prompt method is displayed in the statusMessage*/
        prompt("Search %s (Use ESC/Arrows/Enter)", (query, lastKeyPress) -> {/**second argument of promt is a lambda takes query to
         be searched and a key pressed and then uses them to search*/
            if (query == null || query.isBlank()){
                searchDirection = SearchDirection.FORWARDS;
                lastMatch = -1;
                return;
            }
            if (lastKeyPress == ARROW_LEFT || lastKeyPress == ARROW_UP){
                searchDirection = SearchDirection.BACKWARDS;
            } else if (lastKeyPress == ARROW_RIGHT || lastKeyPress == ARROW_DOWN) {
                searchDirection = SearchDirection.FORWARDS;
            }else {
                searchDirection = SearchDirection.FORWARDS;
                lastMatch = -1;
            }

            int currentInx = lastMatch;

            for (int i = 0; i < content.size(); i++){

                currentInx += searchDirection == SearchDirection.FORWARDS ? 1 : -1;

                if (currentInx == content.size()){
                    currentInx = 0;
                } else if (currentInx == -1) {
                    currentInx = content.size() - 1;
                }

                String currnetLine = content.get(currentInx);
                int match = currnetLine.indexOf(query);
                if (match != -1){
                    lastMatch = currentInx;
                    cursorY = currentInx;
                    cursorX = match; //doesnt work 100%
                    offsetY = content.size();
                    break;
                }
            }
        });
    }
/**prompt works similarly to the saveas and open methods*/
    private static void prompt(String message, BiConsumer<String, Integer> consumer) {

        StringBuilder userInput = new StringBuilder();


            while(true)
            {
                try {
                    setStatusMessage(!userInput.isEmpty() ? userInput.toString() : message);
                    refreshScreen();
                    int key = readKey();

                    if (key == '\033' || key == '\r'){
                        setStatusMessage(null);
                        return;
                    } else if (key == DEL || key == BACKSPACE || key == ctrl_key('h')) {
                        if(!userInput.isEmpty()){
                            userInput.deleteCharAt(userInput.length() - 1);
                        }
                    } else if (!Character.isISOControl(key) && key < 128) {
                        userInput.append((char) key);
                    }

                    consumer.accept(userInput.toString(), key);
                }catch (IOException e){
                    throw new RuntimeException(e);
                    }
            }
    }

    private static int ctrl_key(char key) {
        return key & 0x1f; //bitwise operation to recognise ctrl_key
    }
    private static int shift_key(char key) {
        return key | 0x40; // Apply the bitwise OR operation to set the 6th bit (0x40)
    }

    private static void exit() {
        System.out.print("\033[2J");
        System.out.print("\033[H");
        terminal.disableRawMode();
        System.exit(0);
    }


    private static void moveCursor(int key) {
        String line = currentLine();
        switch(key){
            case ARROW_UP -> {
                if(cursorY > 0) {
                    cursorY--;
                }
            }
            case ARROW_DOWN -> {
                if(cursorY < content.size()){
                    cursorY++;
                }
            }
            case ARROW_LEFT -> {
                if(cursorX > 0){
                    cursorX--;
                }
            }
            case ARROW_RIGHT -> {
                if(line != null && cursorX < line.length()){
                    cursorX++;
                }
            }
            case PG_UP, PG_DOWN -> {

                if(key == PG_UP){
                    moveCursorToTopOfScreen();
                }else if (key == PG_DOWN){
                    moveCursorToBottomOfScreen();
                }

                for(int i = 0; i < rows; i++){
                    moveCursor(key == PG_UP ? ARROW_UP : ARROW_DOWN);
                }
            }
            case HOME -> cursorX = 0;
            case END -> {
                if (line != null) {
                    cursorX = line.length();
                }
            }
        }

        String newLine = currentLine();
        if(newLine != null && cursorX > newLine.length()){
            cursorX = newLine.length();
        }

    }

    private static String currentLine() {
        return cursorY < content.size() ? content.get(cursorY) : null;
    }

    private static void moveCursorToTopOfScreen() {
        cursorY = offsetY;
    }
    private static void moveCursorToBottomOfScreen() {
        cursorY = offsetY + rows - 1;
        if (cursorY > content.size()) cursorY = content.size();
    }


}
interface Terminal {
    void enableRawMode();

    void disableRawMode();

    WindowSize getWindowSize();
}

class UnixTerminal implements Terminal {
    private static LibC.Termios originalAttributes;
    @Override
    public void enableRawMode()
    {/**flags we disable to get terminal using in raw mode*/
        LibC.Termios termios = new LibC.Termios();
        int rc = LibC.INSTANCE.tcgetattr(LibC.SYSTEM_OUT_FD, termios);


        if (rc != 0) {
            System.err.print("There was a problem calling tcgetattr");
            System.exit(rc);
        }

        originalAttributes = LibC.Termios.of(termios);


        termios.c_lflag &= ~(LibC.ECHO | LibC.ICANON | LibC.IEXTEN | LibC.ISIG);
        termios.c_iflag &= ~(LibC.IXON | LibC.ICRNL);
        termios.c_oflag &= ~(LibC.OPOST);

        termios.c_cc[LibC.VMIN] = 0;
        termios.c_cc[LibC.VTIME] = 1;

        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, termios);
    }

    @Override
    public void disableRawMode() {/**returns the original attributes so that console takes commands after exit of JSON editor*/
        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, originalAttributes);
    }

    @Override
    public WindowSize getWindowSize(){/**size of the terminal instance on our OS*/
        final LibC.Winsize winsize = new LibC.Winsize();
        final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.TIOCGWINSZ, winsize);

        if(rc !=0){
            System.err.print("ioctl faied with reurn code[={}]" + rc);
            System.exit(1);
        }
        return new WindowSize(winsize.ws_row, winsize.ws_col);
    }


    interface LibC extends Library{/**extends jna lib*/

        int SYSTEM_OUT_FD = 0;
        int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2,
                IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1, VMIN = 6, VTIME = 5, TIOCGWINSZ = 0x5413;

        /**loading the C standard lib for POSIX systems*/
        LibC INSTANCE = Native.load("c", LibC.class);

        @Structure.FieldOrder(value = {"ws_row","ws_col","ws_xpixel","ws_ypixel"})
        class Winsize extends Structure{
            public short ws_row, ws_col, ws_xpixel, ws_ypixel;
        }

        @Structure.FieldOrder(value = {"c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc"})

        class Termios extends Structure {
            public int c_iflag, c_oflag, c_cflag, c_lflag;
            public byte[] c_cc = new byte[19];


            public Termios() {

            }

            public static Termios of(Termios t) {
                Termios copy = new Termios();
                copy.c_iflag = t.c_iflag;
                copy.c_oflag = t.c_oflag;
                copy.c_cflag = t.c_cflag;
                copy.c_lflag = t.c_lflag;
                copy.c_cc = t.c_cc.clone();
                return copy;
            }

            @Override
            public String toString() {
                return "Termios{" +
                        "c_iflag=" + c_iflag +
                        ", c_oflag" + c_oflag +
                        ", c_cflag" + c_cflag +
                        ", c_lflag" + c_lflag +
                        ", c_cc" + Arrays.toString(c_cc) +
                        '}';
            }
        }
        int tcgetattr(int fd, Termios termios);
        int tcsetattr(int fd, int optional_actions, Termios termios);

        int ioctl(int fd, int opt, Winsize winsize);

    }

}

class MacOsTerminal implements Terminal {
    private static LibC.Termios originalAttributes;
    @Override
    public void enableRawMode()
    {
        LibC.Termios termios = new LibC.Termios();
        int rc = LibC.INSTANCE.tcgetattr(LibC.SYSTEM_OUT_FD, termios);


        if (rc != 0) {
            System.err.print("There was a problem calling tcgetattr");
            System.exit(rc);
        }

        originalAttributes = LibC.Termios.of(termios);


        termios.c_lflag &= ~(LibC.ECHO | LibC.ICANON | LibC.IEXTEN | LibC.ISIG);
        termios.c_iflag &= ~(LibC.IXON | LibC.ICRNL);
        termios.c_oflag &= ~(LibC.OPOST);

        termios.c_cc[LibC.VMIN] = 0;
        termios.c_cc[LibC.VTIME] = 1;

        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, termios);
    }

    @Override
    public void disableRawMode() {
        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, originalAttributes);
    }

    @Override
    public WindowSize getWindowSize(){
        final LibC.Winsize winsize = new LibC.Winsize();
        final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.TIOCGWINSZ, winsize);

        if(rc !=0){
            System.err.print("ioctl faied with reurn code[={}]" + rc);
            System.exit(1);
        }
        return new WindowSize(winsize.ws_row, winsize.ws_col);
    }


    interface LibC extends Library{

        int SYSTEM_OUT_FD = 0;
        int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2,
                IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1, VMIN = 6, VTIME = 5, TIOCGWINSZ = 0x40087468;

        //loading the C standard lib for POSIX systems
        LibC INSTANCE = Native.load("c", LibC.class);

        @Structure.FieldOrder(value = {"ws_row","ws_col","ws_xpixel","ws_ypixel"})
        class Winsize extends Structure{
            public short ws_row, ws_col, ws_xpixel, ws_ypixel;
        }

        @Structure.FieldOrder(value = {"c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc"})

        class Termios extends Structure {
            public long c_iflag, c_oflag, c_cflag, c_lflag;
            public byte[] c_cc = new byte[19];


            public Termios() {

            }

            public static Termios of(Termios t) {
                Termios copy = new Termios();
                copy.c_iflag = t.c_iflag;
                copy.c_oflag = t.c_oflag;
                copy.c_cflag = t.c_cflag;
                copy.c_lflag = t.c_lflag;
                copy.c_cc = t.c_cc.clone();
                return copy;
            }

            @Override
            public String toString() {
                return "Termios{" +
                        "c_iflag=" + c_iflag +
                        ", c_oflag" + c_oflag +
                        ", c_cflag" + c_cflag +
                        ", c_lflag" + c_lflag +
                        ", c_cc" + Arrays.toString(c_cc) +
                        '}';
            }
        }
        int tcgetattr(int fd, Termios termios);
        int tcsetattr(int fd, int optional_actions, Termios termios);

        int ioctl(int fd, int opt, Winsize winsize);

    }

}


/**only few parameters changed to enable windows compatibility*/
class WindowsTerminal implements Terminal {

    private IntByReference inMode;
    private IntByReference outMode;
    @Override
    public void enableRawMode(){
        Pointer inHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_INPUT_HANDLE);

        inMode = new IntByReference();
        Kernel32.INSTANCE.GetConsoleMode(inHandle, inMode);

        int inMode;
        inMode = this.inMode.getValue() & ~(
                Kernel32.ENABLE_ECHO_INPUT
                        |Kernel32.ENABLE_LINE_INPUT
                        |Kernel32.ENABLE_MOUSE_INPUT
                        |Kernel32.ENABLE_WINDOW_INPUT
                        |Kernel32.ENABLE_PROCESSED_INPUT
        );

        inMode |= Kernel32.ENABLE_VIRTUAL_TERMINAL_INPUT;

        Kernel32.INSTANCE.SetConsoleMode(inHandle, inMode);

        Pointer outHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        outMode = new IntByReference();
        Kernel32.INSTANCE.GetConsoleMode(outHandle, outMode);

        int outMode = this.outMode.getValue();
        outMode |= Kernel32.ENABLE_VIRTUAL_TERMINAL_PROCESSING;
        outMode |= Kernel32.ENABLE_PROCESSED_OUTPUT;
        Kernel32.INSTANCE.SetConsoleMode(outHandle, outMode);

    }

    @Override
    public void disableRawMode(){/**disables raw mode after usage, so console takes command after JSON editor exit*/
        Pointer inHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
        Kernel32.INSTANCE.SetConsoleMode(inHandle, inMode.getValue());

        Pointer outHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        Kernel32.INSTANCE.SetConsoleMode(outHandle, outMode.getValue());
    }

    @Override
    public WindowSize getWindowSize(){
        final Kernel32.CONSOLE_SCREEN_BUFFER_INFO info = new Kernel32.CONSOLE_SCREEN_BUFFER_INFO();
        final Kernel32 instance = Kernel32.INSTANCE;
        final Pointer handle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        instance.GetConsoleScreenBufferInfo(handle, info);
        return new WindowSize(info.windowHeight(), info.windowWidth());

    }
    interface Kernel32 extends StdCallLibrary {/**extends jna library*/
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        /**
         *  The CryptUIDlgSelectCertificateFromStore function displays a dialog box
         * that allows the selection of a Certificate from a specific store.
         *
         * @param hCertStore Handle of the certificate store to be searched.
         * @param hwnd Handle of the window for display. If NULL,
         * defaults to desktop window.
         * @param pwszTitle String used as the title of the dialog box. If NULL,
         * the default title, "Select Certificate" is used.
         * @param pwszDisplayString Text statement in the selection dialog box. If NULL,
         * the default phrase. "Select a certificate you want to use" is used.
         * @param dwFlags Currently not used and should be set to 0.
         * @param pvReserved Reserved for future use.
         * @return Returns a pointer to the selected certificate context. If no certificate was selected,
         * NULL is returned. When you have finished using the certificate ,free the certificate context
         * by calling the CertFreeCertificateContext function.
         * */

        public static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004, ENABLE_PROCESSED_OUTPUT = 0x0001;

        int ENABLE_LINE_INPUT = 0x0002;
        int ENABLE_PROCESSED_INPUT = 0x0001;
        int ENABLE_ECHO_INPUT = 0x0004;
        int ENABLE_MOUSE_INPUT = 0x0010;
        int ENABLE_WINDOW_INPUT = 0x0008;
        int ENABLE_QUICK_EDIT_MODE = 0x0040;
        int ENABLE_INSERT_MODE = 0x0020;
        int ENABLE_EXTENDED_FLAGS = 0x0080;
        int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;

        int STD_OUTPUT_HANDLE = -11;
        int STD_INPUT_HANDLE = -10;
        int DISABLE_NEWLINE_AUTO_RETURN = 0X0008;
        /***
        *BOOL WINAPI GetConsoleScreenBufferInfo(
        *_In_ HANDLE hConsoleOutput.
        *_Out_ PConsole_SCREEN_BUFFER_INFO lpConsoleScreenBufferInfo);
         */
        void GetConsoleScreenBufferInfo(
                Pointer in_hConsoleOutput,
                CONSOLE_SCREEN_BUFFER_INFO out_lpConsoleScreenBufferInfo)
                throws LastErrorException;

        void GetConsoleMode(
                Pointer in_hConsoleOutput,
                IntByReference out_lpMode)
                throws LastErrorException;

        void SetConsoleMode(
                Pointer in_hConsoleOutput,
                int in_dwMode)
                throws LastErrorException;

        Pointer GetStdHandle(int nStdHandle);
        /***
        *typedef struct _CONSOLE_SCREEN_BUFFER_INFO{
        * COORD dwSize:
        * COORD dwCursorPosition;
        * WORD wAttributes
        * SMALL_RECT srWindow;
        * COORD dwMaximumWindowSize;
         * } CONSOLE_SCREEN_BUFFER_INFO;*/
        class CONSOLE_SCREEN_BUFFER_INFO extends Structure {
            public COORD dwSize;
            public COORD dwCursorPosition;
            public short wAttributes;
            public SMALL_RECT srWindow;
            public COORD dwMaximumWindowSize;
            private static String[] fieldOrder = {"dwSize", "dwCursorPosition", "wAttributes", "srWindow", "dwMaximumWindowSize"};

            @Override
            protected java.util.List<String> getFieldOrder(){
                return java.util.Arrays.asList(fieldOrder);
            }

            public int windowWidth(){
                return this.srWindow.width() + 1;
            }

            public int windowHeight(){
                return this.srWindow.height() + 1;
            }
        }
        /***
        *typedef struct _COORD {
        *SHORT X;
        *SHORT Y;
        *} COORD, *PCOORD;*/
        class COORD extends Structure implements Structure.ByValue{

            public COORD(){

            }
            public COORD(short X, short Y) {
                this.X = X;
                this.Y = Y;
            }

            public short X;
            public short Y;
            private static String[] fieldOrder = {"X", "Y"};

            @Override
            protected java.util.List<String> getFieldOrder(){
                return java.util.Arrays.asList(fieldOrder);
            }

        }

        /**
        * typedef struct _SMALL_RECT{
         *SHORT Left;
         *SHORT Top;
         *SHORT Right;
         *SHORT Bottom;
        * } SMALL_RECT;*/
        class SMALL_RECT extends Structure{
            public SMALL_RECT(){
            }
            public SMALL_RECT(SMALL_RECT org){
                this(org.Top, org.Left, org.Bottom, org.Right);
            }
            public SMALL_RECT(short Top, short Left, short Bottom, short Right){
                this.Top = Top;
                this.Left = Left;
                this.Bottom = Bottom;
                this.Right = Right;
            }
            public short Left;
            public short Top;
            public short Right;
            public short Bottom;
            private static String[] fieldOrder = {"Left", "Top", "Right", "Bottom"};
            @Override
            protected java.util.List<String> getFieldOrder(){
                return java.util.Arrays.asList(fieldOrder);
            }

            public short width(){
                return(short) (this.Right - this.Left);
            }
            public short height(){
                return(short) (this.Bottom - this.Top);
            }
        }
    }
}
record WindowSize(int rows, int columns){

}

