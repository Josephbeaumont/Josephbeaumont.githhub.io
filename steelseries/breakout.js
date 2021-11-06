var canvas = document.getElementById("myCanvas");
        var ctx = canvas.getContext("2d");
        var score = 0;
        var ballRadius = 10;
        var x1 = canvas.width / 2;
        var y1 = canvas.height - 30;
        var dx1 = 3;
        var dy1 = -3;
        var x1 = canvas.width / 2;
        var y1 = canvas.height - 30;

        var lives = 3;
        var x = canvas.width / 2;
        var y = canvas.height - 30;
        var dx = 2;
        var dy = -2;
        var paddleHeight = 10;
        var paddleWidth = 100;
        var paddleX = (canvas.width - paddleWidth) / 2;
        var rightPressed = false;
        var leftPressed = false;
        var brickRowCount = Math.random() * 19;
        var brickColumnCount = Math.random() * 14;
        var brickWidth = 75;
        var brickHeight = 20;
        var brickPadding = 10;
        var brickOffsetTop = 25;
        var brickOffsetLeft = 30;



        var bricks = [];
        for (var c = 0; c < brickColumnCount; c++) {
            bricks[c] = [];
            for (var r = 0; r < brickRowCount; r++) {
                bricks[c][r] = { x: 0, y: 0, status: 1 };
            }
            for (var c = 0; c < brickColumnCount; c++) {
                bricks[c] = [];
                for (var r = 0; r < brickRowCount; r++) {
                    bricks[c][r] = { x1: 0, y1: 0, status: 1 };
                }
            }
        }

        document.addEventListener("keydown", keyDownHandler, false);
        document.addEventListener("keyup", keyUpHandler, false);
        document.addEventListener("mousemove", mouseMoveHandler, false);

        function mouseMoveHandler(e) {
            var relativeX = e.clientX - canvas.offsetLeft;
            if (relativeX > 0 && relativeX < canvas.width) {
                paddleX = relativeX - paddleWidth / 2;
            }
        }

        function keyDownHandler(e) {
            if (e.key == "Right" || e.key == "ArrowRight") {
                rightPressed = true;
            }
            else if (e.key == "Left" || e.key == "ArrowLeft") {
                leftPressed = true;
            }
        }

        function keyUpHandler(e) {
            if (e.key == "Right" || e.key == "ArrowRight") {
                rightPressed = false;
            }
            else if (e.key == "Left" || e.key == "ArrowLeft") {
                leftPressed = false;
            }

        }

        function collisionDetection() {
            for (var c = 0; c < brickColumnCount; c++) {
                for (var r = 0; r < brickRowCount; r++) {
                    var b = bricks[c][r];
                    if (b.status == 1) {
                        if (x > b.x && x < b.x + brickWidth && y > b.y && y < b.y + brickHeight) {
                            dy = -dy;
                            b.status = 0;
                            score++;
                            if (score == brickRowCount * brickColumnCount) {
                                alert("YOU WIN, CONGRATS!");
                                document.location.reload();
                                clearInterval(interval); // Needed for Chrome to end game
                            }
                        }
                    }
                }
            }
            for (var c = 0; c < brickColumnCount; c++) {
                for (var r = 0; r < brickRowCount; r++) {
                    var b = bricks[c][r];
                    if (b.status == 1) {
                        if (x1 > b.x && x1 < b.x + brickWidth && y1 > b.y && y1 < b.y + brickHeight) {
                            dy1 = -dy1;
                            b.status = 0;
                            score++;
                            if (score == brickRowCount * brickColumnCount) {
                                alert("YOU WIN, CONGRATS!");
                                document.location.reload();
                                clearInterval(interval); // Needed for Chrome to end game
                            }
                        }
                    }
                }
            }

        }
        /*function NewBall() {
            if ()
        }*/



        function drawScore() {
            ctx.font = "16px Comic Sans";
            ctx.fillStyle = "#0095DD";
            ctx.fillText("Score: " + score, 8, 20);
        }

        function drawLives() {
            ctx.font = "16px Arial";
            ctx.fillStyle = "#0095DD";
            ctx.fillText("Lives: " + lives, canvas.width - 65, 20);
        }

        function drawBall() {
            ctx.beginPath();
            ctx.arc(x, y, ballRadius, 0, Math.PI * 2);
            ctx.fillStyle = "#0095DD";
            ctx.fill();
            ctx.closePath();
        }
        function DrawBall2() {
            ctx.beginPath();
            ctx.arc(x1, y1, ballRadius, 0, Math.PI * 2);
            ctx.fillStyle = "red";
            ctx.fill();
            ctx.closePath();
        }


        function drawPaddle() {
            ctx.beginPath();
            ctx.rect(paddleX, canvas.height - paddleHeight, paddleWidth, paddleHeight);
            ctx.fillStyle = "#0095DD";
            ctx.fill();
            ctx.closePath();
        }
        function drawBricks() {
            for (var c = 0; c < brickColumnCount; c++) {
                for (var r = 0; r < brickRowCount; r++) {
                    if (bricks[c][r].status == 1) {
                        var brickX = (c * (brickWidth + brickPadding)) + brickOffsetLeft;
                        var brickY = (r * (brickHeight + brickPadding)) + brickOffsetTop;
                        bricks[c][r].x = brickX;
                        bricks[c][r].y = brickY;
                        ctx.beginPath();
                        ctx.rect(brickX, brickY, brickWidth, brickHeight);
                        ctx.fillStyle = "#0095DD";
                        ctx.fill();
                        ctx.closePath();
                    }
                }
            }
        }



        function draw() {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            drawBricks();
            drawLives();
            drawBall();
            DrawBall2();
            drawPaddle();
            collisionDetection();
            drawScore();

            if (x1 + dx1 > canvas.width - ballRadius || x1 + dx1 < ballRadius) {
                dx1 = -dx1;
            }
            if (y1 + dy1 < ballRadius) {
                dy1 = -dy1;
            }
            else if (y1 + dy1 > canvas.height - ballRadius) {
                if (x1 > paddleX && x1 < paddleX + paddleWidth) {
                    if (y1 = y1 - paddleHeight) {
                        dy1 = Math.random() * -1;
                        dy1 = Math.random() * -6;
                    }
                }
                    lives--;
                    if (!lives) {
                        alert("GAME OVER");
                        document.location.reload();
                        clearInterval(interval); // Needed for Chrome to end game
                    }
                    else {
                        x1 = canvas.width / 2;
                        y1 = canvas.height - 30;
                        dx1 = 2;
                        dy1 = -2;
                        paddleX = (canvas.width - paddleWidth) / 2;
                    
                }
            }


            if (x + dx > canvas.width - ballRadius || x + dx < ballRadius) {
                dx = -dx;
            }
            if (y + dy < ballRadius) {
                dy = -dy;
            }
            else if (y + dy > canvas.height - ballRadius) {
                if (x > paddleX && x < paddleX + paddleWidth) {
                    if (y = y - paddleHeight) {
                        dy = Math.random() * -5; //paddle collsion
                        dy = Math.random() * -3;
                    }
                }
                    lives--;
                    if (!lives) {
                        alert("GAME OVER");
                        document.location.reload();
                        clearInterval(interval); // Needed for Chrome to end game
                    }
                    else {
                        x = canvas.width / 2;
                        y = canvas.height - 30;
                        dx = 2;
                        dy = -2;
                        paddleX = (canvas.width - paddleWidth) / 2;
                    
                }
            }

            if (rightPressed && paddleX < canvas.width - paddleWidth) {
                paddleX += 7;
            }
            else if (leftPressed && paddleX > 0) {
                paddleX -= 7;
            }



            x += dx;
            y += dy;

            x1 += dx1;
            y1 += dy1;
        }

        var interval = setInterval(draw, 10)