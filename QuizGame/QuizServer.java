package QuizGame;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.List;

public class QuizServer {

    private BufferedReader in;
    private BufferedWriter out;
    private ServerSocket listener;
    private Socket socket;
    private String studentID;
    private String studentName;
    private Integer score = 0;
    private int N_QUIZ =  1;

    public QuizServer(int port) {
        try {
            // 서버 연결 설정
            listener = new ServerSocket(port);
            System.out.println("Start Server...");
            System.out.println("Waiting for clients");

            socket = listener.accept();
            System.out.println("A new connection has been established!");

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            sendMsg("A new connection has been established!");     

            BufferedReader br_answer = new BufferedReader(new FileReader("./server_database/answer_page.dat"));
            
            while(handleRequest(getRequest(), br_answer) != -1); // sendFinalScore 실행후 loop 종료
            

            closeConnection();


        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        } 
    }

    private String getRequest() {
        String request;
        System.out.println("Waiting for request...");
        try {
            request = in.readLine();
            if(request == null) {
                System.out.println("ERROR: Request is null");
                closeConnection();
            }
            System.out.println("Get Message: " + request);
        } catch(IOException e) {
            System.out.println("GetRequest error: " + e.getMessage());
            return null;
        }
        return request;
    }

    private int handleRequest(String msg, BufferedReader br_answer ) {
        System.out.println("HandleRequest: " + msg);
        // GET method. 클라이언트가 데이터 요청
        if(msg.startsWith("GET")) {
            String[] method = msg.split(" ");
            System.out.println("method[0]: "+ method[0]);
            System.out.println("method[1]: " + method[1]);
            if(method[1].startsWith("QUIZ[")) { // 새로운 퀴즈 전송 요청
                if(sendQuestion("./server_database/question/q"+ (N_QUIZ++) +".txt") == -1) {
                    sendMsg("SEND ENDQUIZ[]");
                } 
            }
            else if(method[1].startsWith("CHECK[")) { // 정답 확인 요청
                int beginIndex = method[1].indexOf("[");
                int endIndex = method[1].indexOf("]");
                String answer = method[1].substring(beginIndex + 1, endIndex).replace("^", " ");
                checkAnswer(answer, br_answer);
            }
            else if(method[1].startsWith("SCORE[")) { // 점수 전송 요청
                saveGrade(studentID, studentName, score);
                sendFinalScore();
                return -1; // while loop 종료
            }
        }

        // PUT method. 클라이언트가 서버에 저장할 데이터 전송
        else if(msg.startsWith("PUT")) {
            String[] method = msg.split(" ");
            System.out.println("method[0]: "+ method[0]);
            System.out.println("method[1]: " + method[1]);
            if(method[1].startsWith("STUDENT_ID[")) { // 클라이언트가 학번을 전송. 서버에 저장
                int beginIndex = method[1].indexOf("[");
                int endIndex = method[1].indexOf("]");
                String temp_studentID = method[1].substring(beginIndex + 1, endIndex); // STUDENT_ID[학번] 에서 학번 추출
                if(!isDuplicateID(temp_studentID)) {
                    studentID = temp_studentID;
                    System.out.println("SAVED STUDENT_ID");
                }
            }
            else if(method[1].startsWith("STUDENT_NAME[")) { // 클라이언트가 이름을 전송. 서버에 저장
                int beginIndex = method[1].indexOf("[");
                int endIndex = method[1].indexOf("]");
                studentName = method[1].substring(beginIndex + 1, endIndex); // STUDENT_NAME[이름] 에서 이름 추출
                System.out.println("SAVED STUDENT_NAME");
                if(studentID != null && studentName != null ) {
                    System.out.println("저장된 학생 정보: " + studentID + " - " + studentName);
                    sendMsg("SEND STARTQUIZ[]");
                }
            }
        }
        return 0;

    }

    private void sendMsg(String msg) {
        try {
            out.write(msg + "\n"); // 클라이언트에게 시작하라는 메시지 전송
            out.flush();
            System.out.println("Send Message: " + msg);
        } catch(IOException e) {
            System.out.println("SEND error: " + e.getMessage());
        }
    }

    private void checkAnswer(String answer, BufferedReader br_answer) {
        System.out.println("client's answer: " + answer);
        String check;
        // 정답 확인
        //대소문자 처리..
        if(readAnswer(br_answer).equals(answer)) {
            check = "정답입니다!";
            score += 10;
        }
        else {
            check = "오답입니다!";
        }
        sendMsg("SEND CHECK[" + check + "]");
    }

    private String readAnswer(BufferedReader br) { //답지에서 정답 불러오기
        try {
            String answer = br.readLine();
            return answer;
        } catch (IOException e) {
            System.out.println("Error " + e.getMessage());
            return "no answer"; // 기본값 설정
        }
    }

    private int sendQuestion(String fileName) { // 문제지에서 문제 읽고 전송
        try {
            //String Q = br.readLine();
            List<String> lines = Files.readAllLines(Paths.get(fileName));
            // \n은 \\n으로, " "은 ^으로 치환하여 한 줄의 문자열로 변환
            String Q = String.join("\\n", lines).replace(" ","^");

            sendMsg("SEND QUIZ[" + Q + "]");

        } catch (IOException e) {
            System.out.println("quiz end " + e.getMessage()); // 문제가 끝남
            return -1;
        }
        return 0;
    }

    private void saveGrade(String studentID, String studentName, int score) {
        String data = studentID + "  " + studentName + "  " + score + "\n";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("./server_database/grade.dat", true))) { // append 모드 활성화
            writer.write(data);
            System.out.println("Grade saved: " + data.trim());
        } catch (IOException e) {
            System.out.println("Error saving grade: " + e.getMessage());
        }
    }

    // 중복 학번 확인 메서드
    private boolean isDuplicateID(String studentID) { // 학번이 이미 있는 학번인지 중복 확인
        try (BufferedReader reader = new BufferedReader(new FileReader("./data/grade.dat"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("  ");
                if (parts[0].equals(studentID)) {
                    sendMsg("SEND ERROR[DUPLICATED_ID]");
                    return true;
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading grade file: " + e.getMessage());
        }
        return false;
    }    

    public void sendFinalScore() {
        try {
            sendMsg("SEND SCORE[" + score + "]");

            // 메시지 전송 후 잠시 대기
            Thread.sleep(500);
        }  catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        }
    }

    public void closeConnection() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }
    public static void main(String[] args) {
        new QuizServer(8888);  // GUI 실행
    }
    
}



