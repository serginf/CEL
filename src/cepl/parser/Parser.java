package cepl.parser;

import java.io.IOException;
import java.util.ArrayList;

import com.sun.javafx.binding.StringFormatter;

public class Parser {

    private String fileName;
    private ArrayList<Token> tokens;
    private int c_tok;
    private Token lookahead;

    public Parser(String fileName) throws IOException, ParserException {
        this.fileName = fileName;
        Tokenizer tokenizer = new Tokenizer(fileName);
        tokens = tokenizer.getTokens();
    }

    public ASTNode parse() throws ParserException {
        c_tok = 0;
        lookahead = tokens.get(0);
        ASTNode node = parseCEPL();
        if (lookahead.type != TokenType.EOF){
            lookahead.throwError(fileName);
        }
        return node;
    }

    private ASTNode parseCEPL() throws ParserException {
        ASTNode node, a, b;
        node = new ASTNode();
        if (lookahead.type == TokenType.Lp){
            nextToken();
            node = parseCEPL();
            if (lookahead.type != TokenType.Rp){
                lookahead.throwError(fileName);
            }
            nextToken();
        }
        else if (lookahead.type == TokenType.RELATION ){
            node.type = NodeType.ASSIGN;
            node.children.add(new ASTNode(NodeType.RELATION, lookahead));
            nextToken();
            if (lookahead.type != TokenType.AS){
                lookahead.throwError(fileName);
            }
            nextToken();
            if (lookahead.type != TokenType.WORD){
                lookahead.throwError(fileName);
            }
            node.children.add(new ASTNode(NodeType.VARIABLE, lookahead));            
            nextToken();
        }
        
        if (node.isEmpty()) {
            lookahead.throwError(fileName);
        }

        if (lookahead.type == TokenType.PLUS){
            nextToken();
            a = new ASTNode(NodeType.KLEENE);
            a.children.add(node);
            node = a;
        }

        node = parseFilterOp(node);
        node = parseCEPLOP(node);

        return node;
    }


    private ASTNode parseCEPLOP(ASTNode prev) throws ParserException {
        ASTNode node = new ASTNode();
        if (lookahead.type == TokenType.COLON){
            node.type = NodeType.SEQ;
            nextToken();
            node.children.add(prev);
            node.children.add(parseCEPL());
        }
        else if (lookahead.type == TokenType.OR){
            node.type = NodeType.OR;
            nextToken();
            node.children.add(prev);            
            node.children.add(parseCEPL());
        }
        else {
            node = prev;
        }
        return node;
    }

    private ASTNode parseFilterOp(ASTNode prev) throws ParserException {
        ASTNode node, ret;
        node = new ASTNode();
        if (lookahead.type == TokenType.FILTER){
            node.type = NodeType.FILTER;
            nextToken();
            node.children.add(prev);
            node.children.add(parseFilterFormula());
        }
        else {
            node = prev;
        }
        return node;
    }
    
    private ASTNode parseFilterFormula() throws ParserException {
        ASTNode node;
        if (lookahead.type == TokenType.NOT){
            node = new ASTNode(NodeType.PRED_NOT);
            nextToken();
            node.children.add(parseFilterFormula());
        }
        else if (lookahead.type == TokenType.Lp){
            nextToken();
            node = parseFilterFormula();
            if (lookahead.type != TokenType.Rp){
                lookahead.throwError(fileName);
            }
            nextToken();
            node = parseFilterContinuation(node);
        }
        else if (lookahead.type == TokenType.WORD){
            node = new ASTNode(NodeType.PREDICATE);
            node.children.add(parseVariable());
            if (lookahead.type != TokenType.PRED_OP){
                lookahead.throwError(fileName);
            }
            node.children.add(new ASTNode(NodeType.PRED_OP, lookahead));
            nextToken();
            node.children.add(parseExpression());
            node = parseFilterContinuation(node);
        }
        else {
            node = new ASTNode(NodeType.PREDICATE);            
            node.children.add(parseExpression());
            if (lookahead.type != TokenType.PRED_OP){
                lookahead.throwError(fileName);
            }
            node.children.add(new ASTNode(NodeType.PRED_OP, lookahead));  
            nextToken();
            node.children.add(parseVariable());
            node = parseFilterContinuation(node);
        }
        return node;
    }

    private ASTNode parseFilterContinuation(ASTNode prev) throws ParserException {
        ASTNode node = new ASTNode();
        if (lookahead.type == TokenType.FILT_OP){
            node.type = lookahead.sequence.equals("or") ? NodeType.PRED_OR : NodeType.PRED_AND;
            nextToken();
            node.children.add(prev);
            node.children.add(parseFilterFormula());
        }
        else {
            node = prev;
        }
        return node;
    }

    private ASTNode parseVariable() throws ParserException {
        ASTNode node = new ASTNode(NodeType.VAR_PROP);
        if (lookahead.type != TokenType.WORD){
            lookahead.throwError(fileName);   
        }
        node.children.add(new ASTNode(NodeType.VARIABLE, lookahead));
        nextToken();
        if (lookahead.type != TokenType.DOT){
            lookahead.throwError(fileName);               
        }
        nextToken();
        if (lookahead.type != TokenType.WORD){
            lookahead.throwError(fileName);               
        }
        node.children.add(new ASTNode(NodeType.PROPERTY, lookahead));                    
        nextToken();
        return node;
    }

    private ASTNode parseExpression() throws ParserException {
        ASTNode node = new ASTNode(NodeType.VALUE);
        double a = parseSignedTerm();
        TokenType op = lookahead.type;
        double b = parseSumOp();
        if (op == TokenType.PLUS){
            a += b;
        }
        else if (op == TokenType.MINUS){
            a -= b;
        }
        node.value = new Token(null, "" + a, "", 0, 0);
        return node;
    }

    private double parseSumOp() throws ParserException {
        if (lookahead.type == TokenType.PLUS || lookahead.type == TokenType.MINUS){
            nextToken();
            double a = parseSignedTerm();
            TokenType op = lookahead.type;
            double b = parseSumOp();
            if (op == TokenType.PLUS){
                return a + b;
            }
            else if (op == TokenType.MINUS){
                return a - b;
            }
            return a;
        }
        return 0;
    }

    private double parseSignedTerm() throws ParserException {
        if (lookahead.type == TokenType.MINUS){
            nextToken();
            return - parseTerm();
        }
        else{
            return parseTerm();
        }
    }

    private double parseTerm() throws ParserException {
        double a = parseFactor();
        TokenType op = lookahead.type;
        double b = parseTermOp();
        if (op == TokenType.MULT){
            return a * b;
        }
        else if (op == TokenType.DIV){
            return a / b;
        }
        return a;
    }

    private double parseTermOp() throws ParserException {
        if (lookahead.type == TokenType.MULT || lookahead.type == TokenType.DIV){
            nextToken();
            double a = parseSignedFactor();
            TokenType op = lookahead.type;
            double b = parseTermOp();
            if (op == TokenType.MULT){
                return a * b;
            }
            else if (op == TokenType.DIV){
                return a / b;
            }
            return a;
        }
        return 0;
    }

    private double parseSignedFactor() throws ParserException {
        if (lookahead.type == TokenType.MINUS){
            nextToken();
            return -parseFactor();
        }
        else{
            return parseFactor();
        }
    }

    private double parseFactor() throws ParserException {
        if (lookahead.type == TokenType.Lp){
            nextToken();
            double a = Double.parseDouble(parseExpression().value.sequence);

            if (lookahead.type != TokenType.Rp){
                lookahead.throwError(fileName);
            }
            nextToken();
            return a;
        }
        else{
            return parseValue();
        }
    }

    private double parseValue() throws ParserException {
        if (lookahead.type != TokenType.NUMBER){
            lookahead.throwError(fileName);
        }
        double a = Double.parseDouble(lookahead.sequence);
        nextToken();
        return a;
    }


    private void nextToken(){
        lookahead = tokens.get(++c_tok);
    }

    public static void main(String[] argv) throws IOException, ParserException {
        try {
            Parser parser = new Parser(argv[0]);
            ASTNode node = parser.parse();
            node.setVars();
            node.print();
        }
        catch (ParserException e){
            System.out.println(e.getMessage());
        }
    }
}
