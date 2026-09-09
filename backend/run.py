from app import create_app


app = create_app()


if __name__ == "__main__":
    # APScheduler should only run once during local development, so the Werkzeug reloader is disabled here.
    app.run(host="0.0.0.0", port=5000, debug=True, use_reloader=False)
