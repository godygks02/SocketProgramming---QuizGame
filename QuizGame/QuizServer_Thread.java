package QuizGame;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

public class QuizServer_Thread {
    private static final int PORT = 8888;
    private static final int THREAD_POOL_SIZE = 10; // 동시 클라이언트 수 제한
    
    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE); // 스레드풀 생성

        try (ServerSocket listener = new ServerSocket(PORT)) {
            System.out.println("Start Server...");
            System.out.println("Waiting for clients...");

            while (true) {
                // 클라이언트 연결 대기
                Socket socket = listener.accept();
                System.out.println("A new connection has been established!");
                
                // 스레드풀을 통해 각 클라이언트 처리
                threadPool.execute(new ClientHandler(socket)); // 클라이언트 연결 시 새 작업 실행
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        } finally {
            threadPool.shutdown(); // 서버 종료 시 스레드풀 정리
        }
    }
}

class ClientHandler implements Runnable { // Runnable
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String studentID;
    private String studentName;
    private Integer score = 0;
    private int N_QUIZ = 1;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            sendMsg("A new connection has been established!");

            BufferedReader br_answer = new BufferedReader(new FileReader("./server_database/answer_page.dat"));

            while (handleRequest(getRequest(), br_answer) != -1); // sendFinalScore 실행 후 루프 종료

            closeConnection();
        } catch (IOException e) {
            System.out.println("Client handler error: " + e.getMessage());
        }
    }

    private String getRequest() { // 클라이언트가 보낸 메시지 받기
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

    private int handleRequest(String msg, BufferedReader br_answer ) { // 클라이언트가 보낸 메시지 처리
        System.out.println("HandleRequest: " + msg);
        // GET method. 클라이언트가 데이터 요청
        if(msg.startsWith("GET")) {
            String[] method = msg.split(" ");
            System.out.println("method[0]: "+ method[0]);
            System.out.println("method[1]: " + method[1]);
            if(method[1].startsWith("QUIZ[")) { // 새로운 퀴즈 전송 요청
                if(sendQuestion("./server_database/Quiz/q"+ (N_QUIZ++) +".txt") == -1) {
                    sendMsg("SEND ENDQUIZ[]");
                } 
            }
            else if(method[1].startsWith("CHECK[")) { // 정답 확인 요청
                int beginIndex = method[1].indexOf("[");
                int endIndex = method[1].indexOf("]");
                String answer = method[1].substring(beginIndex + 1, endIndex).replace("^"," ");
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
                    saveStudentInfo(); // 학번을 미리 저장(동시 접속 클라이언트에서 중복된 ID로 퀴즈 시작을 방지)
                    sendMsg("SEND STARTQUIZ[]");
                }
            }
        }
        return 0;

    }

    private void sendMsg(String msg) { // 클라이언트에게 메시지 전송
        try {
            out.write(msg + "\n"); 
            out.flush();
            System.out.println("Send Message: " + msg);
        } catch(IOException e) {
            System.out.println("SEND error: " + e.getMessage());
        }
    }

    // 숫자 확인 메서드
    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str); // 문자열을 숫자로 변환 시도
            return true; // 변환 성공하면 숫자
        } catch (NumberFormatException e) {
            return false; // 변환 실패하면 문자열
        }
    }

    private void checkAnswer(String answer, BufferedReader br_answer) { // 정답 확인
        System.out.println("client's answer: " + answer);
        String check;
        // 정답 확인
        String correctAnswer = readAnswer(br_answer); // answer_page.dat에서 정답 읽기

        // answer가 숫자이면 그대로 비교
        if (isNumeric(answer)) {
            if (correctAnswer.equals(answer)) {
                check = "정답입니다!";
                score += 10;
            } else {
                check = "오답입니다!";
            }
        }
        // answer가 문자열이면 대소문자 구분 없이 비교
        else {
            if (correctAnswer.equalsIgnoreCase(answer)) {
                check = "정답입니다!";
                score += 10;
            } else {
                check = "오답입니다!";
            }
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

    private void saveGrade(String studentID, String studentName, int score) { // 미리 저장해둔 학번의 점수를 업데이트
        File inputFile = new File("./server_database/grade.dat");
        File tempFile = new File("./server_database/grade_temp.dat");
    
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
    
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("  ");
                if (parts[0].equals(studentID)) {
                    // 학번이 일치하면 점수 업데이트
                    writer.write(parts[0] + "  " + parts[1] + "  " + score + "\n");
                } else {
                    // 학번이 일치하지 않으면 그대로 기록
                    writer.write(line + "\n");
                }
            }
            reader.close(); // 파일을 읽은 후 닫기
            writer.close(); // 파일을 쓴 후 닫기
            
            // 기존 파일을 덮어쓰고 임시 파일을 삭제
            inputFile.delete();
            tempFile.renameTo(inputFile);
    
        } catch (IOException e) {
            System.out.println("Error updating final score: " + e.getMessage());
        }
        System.out.println("Saved Grade");
    }
    

    // 중복 학번 확인 메서드
    private boolean isDuplicateID(String studentID) { // 학번이 이미 있는 학번인지 중복 확인
        try (BufferedReader reader = new BufferedReader(new FileReader("./server_database/grade.dat"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("  ");
                if (parts[0].equals(studentID)) {
                    sendMsg("SEND ERROR[DUPLICATED_ID]"); // 중복일 경우 메시지 전송
                    return true;
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading grade file: " + e.getMessage());
        }
        return false;
    }    

    private void saveStudentInfo() { //미리 학번 이름을 저장
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("./server_database/grade.dat", true))) {
            writer.write(studentID + "  " + studentName + "  0\n");  // 점수는 0으로 초기화
            System.out.println("Saved StudentInfo: " + studentID + ", " + studentName);
        } catch (IOException e) {
            System.out.println("Error saving student info: " + e.getMessage());
        }
    }

    public void sendFinalScore() { // 최종 점수 전송
        try {
            sendMsg("SEND SCORE[" + score + "]");

            // 메시지 전송 후 잠시 대기
            Thread.sleep(500);
        }  catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        }
    }

    private void closeConnection() { // 연결 종료
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }
}

